package com.example.workouttracker.data.backup

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// Requirements for obtaining and revoking Google Drive authorization
interface GoogleAuthorizationGateway {
    // Return whether Google authorization can run on this device
    fun isAvailable(): Boolean

    // Request Drive permission from the user
    suspend fun authorize()

    // Remove the app's existing Drive permission
    suspend fun revoke()

    // Return whether Drive permission has already been granted
    suspend fun hasAuthorization(): Boolean

    // Return a fresh token for a Google Drive REST request
    suspend fun accessToken(): String
}

// Convert Google's Activity-based authorization into functions which coroutines can wait for
class AndroidGoogleAuthorizationGateway(
    private val activity: ComponentActivity,
) : GoogleAuthorizationGateway {
    private val client = Identity.getAuthorizationClient(activity)
    private val scopes = listOf(Scope(Scopes.DRIVE_FILE))
    private var latestResult: AuthorizationResult? = null
    private var resultContinuation: kotlin.coroutines.Continuation<ActivityResult>? = null

    private val resolutionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val continuation = resultContinuation ?: return@registerForActivityResult
        resultContinuation = null
        // Return Google's complete response so configuration errors are not mistaken for cancellation
        continuation.resume(result)
    }

    // Return whether Google Play Services is available on this device
    override fun isAvailable(): Boolean =
        com.google.android.gms.common.GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(activity) == com.google.android.gms.common.ConnectionResult.SUCCESS

    // Request Drive access and confirm that Google returned a token
    override suspend fun authorize() {
        latestResult = requestAuthorization(allowUserInteraction = true)
        requireNotNull(latestResult?.accessToken) { "Google did not return a Drive access token." }
    }

    // Revoke access for the currently authorized Google account
    override suspend fun revoke() {
        val result = latestResult ?: runCatching { requestAuthorization(false) }.getOrNull()
        val account = result?.toGoogleSignInAccount()?.account
        if (account != null) {
            client.revokeAccess(
                RevokeAccessRequest.builder()
                    .setAccount(account)
                    .setScopes(scopes)
                    .build(),
            ).await()
        }
        latestResult = null
    }

    // Check for existing Drive access without opening a user interface
    override suspend fun hasAuthorization(): Boolean =
        runCatching {
            val result = requestAuthorization(allowUserInteraction = false)
            latestResult = result
            result.accessToken != null
        }.getOrDefault(false)

    // Return a fresh access token for a Drive request
    override suspend fun accessToken(): String {
        // Requesting authorization again refreshes an expired token without showing a dialog
        val result = requestAuthorization(allowUserInteraction = false)
        latestResult = result
        return requireNotNull(result.accessToken) { "Google Drive authorization has expired. Reconnect Drive." }
    }

    // Complete authorization immediately or open Google's consent screen when allowed
    private suspend fun requestAuthorization(allowUserInteraction: Boolean): AuthorizationResult {
        val request = AuthorizationRequest.builder().setRequestedScopes(scopes).build()
        val initial = try {
            client.authorize(request).await()
        } catch (error: Throwable) {
            throw error.asReadableAuthorizationError()
        }
        if (!initial.hasResolution()) return initial
        check(allowUserInteraction) { "Google Drive needs authorization. Reconnect Drive." }
        check(resultContinuation == null) { "A Google authorization request is already open." }
        val activityResult = suspendCancellableCoroutine { continuation ->
            resultContinuation = continuation
            continuation.invokeOnCancellation { resultContinuation = null }
            val pendingIntent = requireNotNull(initial.pendingIntent) {
                "Google authorization could not be opened."
            }
            try {
                resolutionLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } catch (error: Throwable) {
                resultContinuation = null
                continuation.resumeWithException(error.asReadableAuthorizationError())
            }
        }

        // Decode Google's returned error before treating the result as a normal cancellation
        activityResult.data?.let { responseIntent ->
            try {
                return client.getAuthorizationResultFromIntent(responseIntent)
            } catch (error: Throwable) {
                throw error.asReadableAuthorizationError()
            }
        }
        if (activityResult.resultCode == Activity.RESULT_CANCELED) {
            throw IllegalStateException(
                "Google Drive authorization did not complete. If you did not close Google's screen, " +
                    "check the app's OAuth package name and signing certificate in Google Cloud.",
            )
        }
        throw IllegalStateException("Google Drive authorization did not return a result.")
    }
}

// Convert Google Play Services status codes into user-friendly messages
private fun Throwable.asReadableAuthorizationError(): Throwable {
    val apiError = this as? ApiException ?: return this
    val message = when (apiError.statusCode) {
        CommonStatusCodes.CANCELED,
        SIGN_IN_CANCELLED_STATUS,
        -> "Google Drive authorization did not complete. If you did not close Google's screen, " +
            "check the app's OAuth package name and signing certificate in Google Cloud."

        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google Drive sign-in is not configured for this app build. " +
                "Register its package name and signing certificate in Google Cloud, then enable the Drive API."

        CommonStatusCodes.NETWORK_ERROR ->
            "Google Drive could not connect. Check the internet connection and try again."

        CommonStatusCodes.INTERNAL_ERROR ->
            "Google rejected this app's Drive authorization. Check that an Android OAuth client " +
                "matches this build's package name and signing SHA-1 in Google Cloud."

        ConnectionResult.SERVICE_MISSING ->
            "Google Play Services is not installed on this device."

        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ->
            "Google Play Services must be updated before connecting Google Drive."

        ConnectionResult.SERVICE_DISABLED ->
            "Google Play Services is disabled on this device."

        else -> "Google Drive authorization failed (${apiError.statusCode}). Please try again."
    }
    return IllegalStateException(message, apiError)
}

// Google Sign-In uses this status when the user closes the account or consent screen
private const val SIGN_IN_CANCELLED_STATUS = 12501

// Wait for a Google Task using a cancellable coroutine
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
