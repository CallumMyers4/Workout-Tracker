package com.example.workouttracker.feature.workoutdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouttracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Load the selected workout and handle actions from its details page
class WorkoutDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<WorkoutDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<WorkoutDetailEvent> = _events.asSharedFlow()
    private val workoutId: Long? = savedStateHandle["workoutId"]

    // Load the workout ID supplied by navigation and observe future changes to it
    init {
        val id = workoutId
        if (id == null || id <= 0L) {
            _uiState.value = WorkoutDetailUiState(errorMessage = "Workout not found.")
        } else {
            viewModelScope.launch {
                workoutRepository.observeWorkout(id)
                    .onStart { _uiState.update { it.copy(isLoading = true, errorMessage = null) } }
                    .catch { error ->
                        _uiState.update {
                            it.copy(workout = null, isLoading = false, errorMessage = error.userMessage())
                        }
                    }
                    .collect { workout ->
                        _uiState.update {
                            it.copy(
                                workout = workout,
                                isLoading = false,
                                errorMessage = if (workout == null) "Workout not found." else null,
                            )
                        }
                    }
            }
        }
    }

    // Open the delete confirmation dialog when deletion is available
    fun requestDelete() {
        if (_uiState.value.workout != null && !_uiState.value.isDeleting) {
            _uiState.update { it.copy(showDeleteConfirmation = true) }
        }
    }

    // Close the delete confirmation dialog
    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    // Permanently delete the workout and notify the app to leave the page
    fun confirmDelete() {
        val id = workoutId ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(showDeleteConfirmation = false, isDeleting = true) }
        viewModelScope.launch {
            runCatching { workoutRepository.deleteWorkout(id) }
                .onSuccess { _events.emit(WorkoutDetailEvent.Deleted) }
                .onFailure { error ->
                    _uiState.update { it.copy(isDeleting = false, errorMessage = error.userMessage()) }
                }
        }
    }

    // Clear the currently displayed error
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Give a user-friendly generic error
    private fun Throwable.userMessage(): String = message ?: "Something went wrong. Please try again."
}
