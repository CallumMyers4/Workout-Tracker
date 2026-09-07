package com.example.workouttracker.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.AppNotification
import com.example.workouttracker.core.model.AppNotificationType
import com.example.workouttracker.domain.repository.BackupRepository
import com.example.workouttracker.domain.repository.ExerciseRepository
import com.example.workouttracker.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.example.workouttracker.domain.repository.BackupConnectionState

// Manage settings values and actions while the settings page is open
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val exerciseRepository: ExerciseRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    // Observe preference, exercise library, and Drive changes together
    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.preferences,
                exerciseRepository.observeCatalog(),
                backupRepository.connectionState,
            ) { preferences, exercises, backup -> Triple(preferences, exercises, backup) }
                .catch { error -> _uiState.update { it.copy(errorMessage = error.userMessage()) } }
                .collect { (preferences, exercises, backup) ->
                    _uiState.update {
                        it.copy(preferences = preferences, exercises = exercises, backupState = backup)
                    }
                }
        }
    }

    // Update whether the dark theme is enabled
    fun setDarkTheme(enabled: Boolean) {
        updatePreferences { it.copy(darkTheme = enabled) }
    }

    // Update the unit used to display weights
    fun setWeightsUnit(weightsUnit: WeightsUnit) {
        updatePreferences { it.copy(weightsUnit = weightsUnit) }
    }

    // Update the default workout date filter
    fun setFilter(filter: WorkoutFilter) {
        updatePreferences { it.copy(filter = filter) }
    }

    // Update the default workout sorting order
    fun setSort(sort: WorkoutSort) {
        updatePreferences { it.copy(sort = sort) }
    }

    // Update the default workout grouping method
    fun setGrouping(grouping: WorkoutGrouping) {
        updatePreferences { it.copy(grouping = grouping) }
    }

    // Reset all saved preferences to their default values
    fun resetPreferences() {
        launchOperation("Preferences reset.") { preferencesRepository.reset() }
    }

    // Open the exercise library dialog
    fun showExerciseLibrary() {
        _uiState.update { it.copy(isExerciseLibraryVisible = true, errorMessage = null) }
    }

    // Close the exercise library and any dialog opened from it
    fun hideExerciseLibrary() {
        _uiState.update { it.copy(isExerciseLibraryVisible = false, exerciseDialog = null) }
    }

    // Add a new exercise after validating its name
    fun addExercise(name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Exercise name cannot be blank.") }
            return
        }
        launchOperation("Exercise added.") { exerciseRepository.addExercise(cleanName) }
    }

    // Rename an exercise or ask to combine it when the new name already exists
    fun renameExercise(exerciseId: Long, newName: String) {
        val cleanName = newName.trim()
        val source = _uiState.value.exercises.firstOrNull { it.id == exerciseId } ?: return
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Exercise name cannot be blank.") }
            return
        }
        val target = _uiState.value.exercises.firstOrNull {
            it.id != exerciseId && it.name.equals(cleanName, ignoreCase = true)
        }
        if (target != null) {
            _uiState.update {
                it.copy(
                    exerciseDialog = ExerciseDialogState.ConfirmCombine(
                        source.id, source.name, target.id, target.name,
                    ),
                )
            }
        } else {
            launchOperation("Exercise renamed.") {
                exerciseRepository.renameExercise(exerciseId, cleanName)
            }
        }
    }

    // Delete an exercise when it has no saved workout history
    fun deleteExercise(exerciseId: Long) {
        launchOperation("Exercise deleted.") { exerciseRepository.deleteExercise(exerciseId) }
    }

    // Move workout history into the selected target exercise
    fun combineExercises(sourceExerciseId: Long, targetExerciseId: Long) {
        val confirmation = _uiState.value.exerciseDialog as? ExerciseDialogState.ConfirmCombine
        if (confirmation != null &&
            (confirmation.sourceId != sourceExerciseId || confirmation.targetId != targetExerciseId)
        ) return
        launchOperation("Exercises combined.") {
            exerciseRepository.combineExercises(sourceExerciseId, targetExerciseId)
            _uiState.update { it.copy(exerciseDialog = null) }
        }
    }

    // Connect to or disconnect from Google Drive
    fun signInOrOut() {
        if (_uiState.value.backupState.isBusy()) return
        viewModelScope.launch {
            val wasConnected = _uiState.value.backupState == BackupConnectionState.Connected
            runCatching {
                when (_uiState.value.backupState) {
                    BackupConnectionState.Connected -> backupRepository.signOut()
                    BackupConnectionState.SignedOut,
                    is BackupConnectionState.Error -> backupRepository.signIn()
                    else -> Unit
                }
            }.onSuccess {
                notifySuccess(if (wasConnected) "Google Drive disconnected." else "Google Drive connected.")
            }.onFailure(::notifyError)
        }
    }

    // Open the confirmation dialog for creating a backup
    fun requestBackup() {
        if (_uiState.value.backupState == BackupConnectionState.Connected) {
            _uiState.update { it.copy(showBackupConfirmation = true) }
        }
    }

    // Create the Drive backup after confirmation
    fun confirmBackup() {
        _uiState.update { it.copy(showBackupConfirmation = false) }
        viewModelScope.launch {
            runCatching { backupRepository.backup() }
                .onSuccess { notifySuccess("Backup completed.") }
                .onFailure(::notifyError)
        }
    }

    // Open the confirmation dialog for restoring a backup
    fun requestRestore() {
        if (_uiState.value.backupState == BackupConnectionState.Connected) {
            _uiState.update { it.copy(showRestoreConfirmation = true) }
        }
    }

    // Restore the Drive backup after confirmation
    fun confirmRestore() {
        _uiState.update { it.copy(showRestoreConfirmation = false) }
        viewModelScope.launch {
            runCatching { backupRepository.restore() }
                .onSuccess { notifySuccess("Backup restored.") }
                .onFailure(::notifyError)
        }
    }

    // Close any open backup or restore confirmation dialog
    fun dismissConfirmation() {
        _uiState.update {
            it.copy(showBackupConfirmation = false, showRestoreConfirmation = false)
        }
    }

    // Save a preference update and display any error
    private fun updatePreferences(transform: (com.example.workouttracker.core.model.AppPreferences) -> com.example.workouttracker.core.model.AppPreferences) {
        viewModelScope.launch {
            runCatching { preferencesRepository.update(transform) }
                .onFailure(::notifyError)
        }
    }

    // Run an exercise library operation and display any error
    private fun launchOperation(successMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { notifySuccess(successMessage) }
                .onFailure(::notifyError)
        }
    }

    // Emit a green one-time result without retaining it in settings state
    private fun notifySuccess(message: String) {
        _events.tryEmit(
            SettingsEvent(AppNotification(message, AppNotificationType.SUCCESS)),
        )
    }

    // Emit a red one-time result with a user-friendly failure message
    private fun notifyError(error: Throwable) {
        _events.tryEmit(
            SettingsEvent(AppNotification(error.userMessage(), AppNotificationType.ERROR)),
        )
    }

    // Return whether a Google Drive operation is currently running
    private fun BackupConnectionState.isBusy(): Boolean =
        this == BackupConnectionState.Authorizing ||
            this == BackupConnectionState.Uploading ||
            this == BackupConnectionState.Restoring

    // Give a user-friendly generic error
    private fun Throwable.userMessage(): String = message ?: "Something went wrong. Please try again."
}
