package com.example.workouttracker.domain.repository

import kotlinx.coroutines.flow.Flow

// Requirements for connecting to Drive and backing up or restoring app data
interface BackupRepository {
    // Observe the current Drive connection or active operation
    val connectionState: Flow<BackupConnectionState>

    // Connect the app to Google Drive
    suspend fun signIn()

    // Disconnect the app from Google Drive
    suspend fun signOut()

    // Upload the current database to Drive
    suspend fun backup()

    // Replace local data with the latest Drive backup
    suspend fun restore()
}

// Current Google Drive connection or operation shown on the settings page
sealed interface BackupConnectionState {
    data object Unavailable : BackupConnectionState
    data object SignedOut : BackupConnectionState
    data object Authorizing : BackupConnectionState
    data object Connected : BackupConnectionState
    data object Uploading : BackupConnectionState
    data object Restoring : BackupConnectionState
    data class Error(val message: String) : BackupConnectionState
}
