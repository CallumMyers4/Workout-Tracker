package com.example.workouttracker.data.backup

import com.example.workouttracker.domain.repository.BackupConnectionState
import com.example.workouttracker.domain.repository.BackupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Coordinate Google authorization, Drive transfers, and safe database checkpoints
class DriveBackupRepository(
    private val authorizationGateway: GoogleAuthorizationGateway,
    private val driveGateway: GoogleDriveGateway,
    private val checkpoint: DatabaseCheckpoint,
) : BackupRepository {
    // Prevent backup, restore, sign-in, and sign-out operations from running at the same time
    private val state = MutableStateFlow<BackupConnectionState>(BackupConnectionState.SignedOut)
    private val operationMutex = Mutex()
    private val initializationMutex = Mutex()
    private var hasCheckedInitialAuthorization = false
    // Check Google authorization once when this repository is first observed
    override val connectionState: Flow<BackupConnectionState> = state.asStateFlow().onStart {
        initializationMutex.withLock {
            if (!hasCheckedInitialAuthorization) {
                hasCheckedInitialAuthorization = true
                if (!authorizationGateway.isAvailable()) {
                    state.value = BackupConnectionState.Unavailable
                } else if (
                    state.value == BackupConnectionState.SignedOut &&
                    authorizationGateway.hasAuthorization()
                ) {
                    state.value = BackupConnectionState.Connected
                }
            }
        }
    }

    // Ask the user to authorize Google Drive access
    override suspend fun signIn() {
        operationMutex.withLock {
            if (!authorizationGateway.isAvailable()) {
                state.value = BackupConnectionState.Unavailable
                return@withLock
            }
            state.value = BackupConnectionState.Authorizing
            try {
                authorizationGateway.authorize()
                state.value = BackupConnectionState.Connected
            } catch (error: Throwable) {
                state.value = BackupConnectionState.Error(error.userMessage())
                // Let the caller distinguish a failed connection from a completed one
                throw error
            }
        }
    }

    // Revoke Google Drive access and return to the signed-out state
    override suspend fun signOut() {
        operationMutex.withLock {
            try {
                authorizationGateway.revoke()
            } finally {
                state.value = BackupConnectionState.SignedOut
            }
        }
    }

    // Create a database checkpoint and upload it as the latest backup
    override suspend fun backup() {
        runExclusive(BackupConnectionState.Uploading) {
            val temporary = checkpoint.temporaryFile("backup")
            try {
                driveGateway.uploadOrReplace(
                    checkpoint.create(temporary),
                    BACKUP_FOLDER,
                    BACKUP_FILE,
                )
            } finally {
                temporary.delete()
            }
        }
    }

    // Download, validate, and restore the latest database backup
    override suspend fun restore() {
        runExclusive(BackupConnectionState.Restoring) {
            val temporary = checkpoint.temporaryFile("restore")
            try {
                val candidate = driveGateway.downloadLatest(BACKUP_FOLDER, BACKUP_FILE, temporary)
                checkpoint.validate(candidate)
                checkpoint.restore(candidate)
            } finally {
                temporary.delete()
            }
        }
    }

    // Run one Drive operation and update the state shown on the settings page
    private suspend fun runExclusive(
        workingState: BackupConnectionState,
        operation: suspend () -> Unit,
    ) {
        check(state.value == BackupConnectionState.Connected) { "Connect Google Drive first." }
        check(operationMutex.tryLock()) { "Another backup operation is already running." }
        try {
            state.value = workingState
            try {
                operation()
                state.value = BackupConnectionState.Connected
            } catch (error: Throwable) {
                if (error is InvalidDriveAuthorizationException) {
                    runCatching { authorizationGateway.revoke() }
                }
                state.value = BackupConnectionState.Error(error.userMessage())
                // Preserve the error state while allowing the UI to show a failure result
                throw error
            }
        } finally {
            operationMutex.unlock()
        }
    }

    // Give a user-friendly generic Drive error
    private fun Throwable.userMessage(): String = message ?: "The Google Drive operation failed."

    private companion object {
        const val BACKUP_FOLDER = "Workout Tracker Backups"
        const val BACKUP_FILE = "workout-tracker.db"
    }
}
