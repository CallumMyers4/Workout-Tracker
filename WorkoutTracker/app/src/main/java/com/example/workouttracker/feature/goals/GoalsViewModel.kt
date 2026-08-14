package com.example.workouttracker.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouttracker.domain.repository.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GoalsViewModel(
    private val goalRepository: GoalRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    // When goals page is opened
    init {
        viewModelScope.launch {
            // Keep track of loading progress and errors for the page
            goalRepository.observeProgress()
                .onStart { _uiState.update { it.copy(isLoading = true, errorMessage = null) } }
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.userMessage()) }
                }
                // Keep track of loading progress and errors for each goals card
                .collect { goals ->
                    _uiState.update { it.copy(goals = goals, isLoading = false, errorMessage = null) }
                }
        }
    }

    // Create the dialog for updating each goal
    fun openGoalEditor(exerciseId: Long) {
        val exercise = _uiState.value.goals.firstOrNull { it.exercise.id == exerciseId } ?: return
        _uiState.update {
            it.copy(
                editor = GoalEditorState(
                    exerciseId = exerciseId,
                    exerciseName = exercise.exercise.name,
                    input = exercise.exercise.goalKg?.toString().orEmpty(),
                ),
            )
        }
    }

    // Update the input of a goal
    fun updateGoalInput(value: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(input = value, errorMessage = null))
        }
    }

    // Close the window which edits a goal
    fun dismissGoalEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    // Save an updated goal value
    fun saveGoal() {
        val editor = _uiState.value.editor ?: return
        val goal = if (editor.input.isBlank()) null else editor.input.toDoubleOrNull()
        if (goal != null && (!goal.isFinite() || goal <= 0.0) || goal == null && editor.input.isNotBlank()) {
            _uiState.update { it.copy(editor = editor.copy(errorMessage = "Enter a positive number, or leave it blank.")) }
            return
        }
        viewModelScope.launch {
            runCatching { goalRepository.updateGoal(editor.exerciseId, goal) }
                .onSuccess { _uiState.update { it.copy(editor = null) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(editor = editor.copy(errorMessage = error.userMessage()))
                    }
                }
        }
    }

    // Give a user-friendly generic error
    private fun Throwable.userMessage(): String = message ?: "Something went wrong. Please try again."
}
