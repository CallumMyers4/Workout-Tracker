package com.example.workouttracker.data.backup

import com.example.workouttracker.domain.repository.BackupConnectionState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Check backup state changes and cleanup using local test implementations
class DriveBackupRepositoryTest {
    @Test
    fun unavailableServicesAreRetriedAndRestoreExistingAuthorization() = runTest {
        val authorization = FakeAuthorization().apply {
            available = false
            authorized = true
        }
        val repository = DriveBackupRepository(authorization, FakeDriveGateway(), FakeCheckpoint())

        assertEquals(BackupConnectionState.Unavailable, repository.connectionState.first())
        assertEquals(BackupConnectionState.Unavailable, repository.connectionState.first())
        authorization.available = true

        assertEquals(BackupConnectionState.Connected, repository.connectionState.first())
    }

    @Test
    fun recoveredServicesWithoutAuthorizationAllowSignIn() = runTest {
        val authorization = FakeAuthorization().apply { available = false }
        val repository = DriveBackupRepository(authorization, FakeDriveGateway(), FakeCheckpoint())

        assertEquals(BackupConnectionState.Unavailable, repository.connectionState.first())
        authorization.available = true

        assertEquals(BackupConnectionState.SignedOut, repository.connectionState.first())
        repository.signIn()
        assertEquals(BackupConnectionState.Connected, repository.connectionState.first())
    }

    @Test
    fun signOutWhileInitializationIsPendingPreventsAutomaticReconnection() = runTest {
        val authorization = FakeAuthorization(clearAuthorizationOnRevoke = false).apply {
            available = false
            authorized = true
        }
        val repository = DriveBackupRepository(authorization, FakeDriveGateway(), FakeCheckpoint())

        assertEquals(BackupConnectionState.Unavailable, repository.connectionState.first())
        repository.signOut()
        authorization.available = true

        assertEquals(BackupConnectionState.SignedOut, repository.connectionState.first())
    }

    @Test
    // Keep an explicit disconnect when a new Settings collector starts observing
    fun reconnectingCollectorDoesNotUndoSignOut() = runTest {
        val authorization = FakeAuthorization(clearAuthorizationOnRevoke = false)
        val repository = DriveBackupRepository(
            authorizationGateway = authorization,
            driveGateway = FakeDriveGateway(),
            checkpoint = FakeCheckpoint(),
        )

        repository.connectionState.first()
        repository.signIn()
        repository.signOut()

        assertEquals(BackupConnectionState.SignedOut, repository.connectionState.first())
    }

    @Test
    // Confirm that expired authorization disconnects Drive and deletes the temporary file
    fun invalidAuthorizationDisconnectsAndCleansTemporaryFile() = runTest {
        val authorization = FakeAuthorization()
        val checkpoint = FakeCheckpoint()
        val repository = DriveBackupRepository(
            authorizationGateway = authorization,
            driveGateway = object : GoogleDriveGateway {
                override suspend fun uploadOrReplace(
                    localFile: File,
                    folderName: String,
                    remoteName: String,
                ): String = throw InvalidDriveAuthorizationException("Expired")

                override suspend fun downloadLatest(
                    folderName: String,
                    remoteName: String,
                    destination: File,
                ): File = destination
            },
            checkpoint = checkpoint,
        )

        repository.connectionState.first()
        repository.signIn()
        var failed = false
        try {
            repository.backup()
        } catch (_: InvalidDriveAuthorizationException) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(authorization.revoked)
        assertTrue(repository.connectionState.first() is BackupConnectionState.Error)
        assertFalse(checkpoint.temporary.exists())
    }

    // Simulate Google authorization without contacting Google Play Services
    private class FakeAuthorization(
        private val clearAuthorizationOnRevoke: Boolean = true,
    ) : GoogleAuthorizationGateway {
        var authorized = false
        var revoked = false
        var available = true
        override fun isAvailable() = available
        override suspend fun authorize() { authorized = true }
        override suspend fun revoke() {
            if (clearAuthorizationOnRevoke) authorized = false
            revoked = true
        }
        override suspend fun hasAuthorization() = authorized
        override suspend fun accessToken() = "token"
    }

    // Provide Drive operations which complete without external network access
    private class FakeDriveGateway : GoogleDriveGateway {
        override suspend fun uploadOrReplace(
            localFile: File,
            folderName: String,
            remoteName: String,
        ) = "file-id"

        override suspend fun downloadLatest(
            folderName: String,
            remoteName: String,
            destination: File,
        ) = destination
    }

    // Reuse one temporary file while recording checkpoint cleanup
    private class FakeCheckpoint : DatabaseCheckpoint {
        val temporary = File.createTempFile("checkpoint-test-", ".db")
        override fun temporaryFile(prefix: String) = temporary
        override suspend fun create(destination: File) = destination
        override suspend fun validate(candidate: File) = Unit
        override suspend fun restore(candidate: File) = Unit
    }
}
