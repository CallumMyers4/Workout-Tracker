package com.example.workouttracker.data.backup

import com.example.workouttracker.domain.repository.BackupConnectionState
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Check backup state changes and cleanup using local test implementations
class DriveBackupRepositoryTest {
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
        repository.backup()

        assertTrue(authorization.revoked)
        assertTrue(repository.connectionState.first() is BackupConnectionState.Error)
        assertFalse(checkpoint.temporary.exists())
    }

    // Simulate Google authorization without contacting Google Play Services
    private class FakeAuthorization : GoogleAuthorizationGateway {
        var authorized = false
        var revoked = false
        override fun isAvailable() = true
        override suspend fun authorize() { authorized = true }
        override suspend fun revoke() { authorized = false; revoked = true }
        override suspend fun hasAuthorization() = authorized
        override suspend fun accessToken() = "token"
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
