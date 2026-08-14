package com.example.workouttracker.feature.settings

import com.example.workouttracker.core.model.AppPreferences
import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.domain.repository.BackupConnectionState

// Current state of the settings page
data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val exercises: List<CatalogExercise> = emptyList(),
    val backupState: BackupConnectionState = BackupConnectionState.SignedOut,
    val isExerciseLibraryVisible: Boolean = false,
    val showBackupConfirmation: Boolean = false,
    val showRestoreConfirmation: Boolean = false,
    val exerciseDialog: ExerciseDialogState? = null,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null,
)

// Current exercise library action which requires another dialog
sealed interface ExerciseDialogState {
    // Exercise currently being renamed
    data class Rename(val exerciseId: Long, val input: String) : ExerciseDialogState
    // Existing exercises which will be combined after confirmation
    data class ConfirmCombine(
        val sourceId: Long,
        val sourceName: String,
        val targetId: Long,
        val targetName: String,
    ) : ExerciseDialogState
}
