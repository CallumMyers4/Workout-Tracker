package com.example.workouttracker.feature.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.CardioEntryDraft
import com.example.workouttracker.core.model.durationSecondsOrNull
import kotlinx.coroutines.flow.combine
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
    private var weightsUnit = WeightsUnit.METRIC
    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()

    // When goals page is opened
    init {
        viewModelScope.launch {
            // Keep track of loading progress and errors for the page
            combine(goalRepository.observeProgress(), goalRepository.observeCardioProgress()) { strength, cardio -> strength to cardio }
                .onStart { _uiState.update { it.copy(isLoading = true, errorMessage = null) } }
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.userMessage()) }
                }
                // Keep track of loading progress and errors for each goals card
                .collect { (goals, cardio) ->
                    _uiState.update { it.copy(goals = goals, cardioGoals = cardio, isLoading = false, errorMessage = null) }
                }
        }
    }

    // Convert an open goal editor when the selected display unit changes
    fun setWeightsUnit(unit: WeightsUnit) {
        val previous = weightsUnit
        if (unit == previous) return
        weightsUnit = unit
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            val value = editor.input.toDoubleOrNull() ?: return@update state
            state.copy(
                editor = editor.copy(
                    input = if (editor.type == WorkoutType.STRENGTH) {
                        unit.format(unit.fromKilograms(previous.toKilograms(value)))
                    } else unit.format(unit.fromMeters(previous.toMeters(value))),
                ),
            )
        }
    }

    fun selectType(type: WorkoutType) { _uiState.update { it.copy(selectedType = type, editor = null) } }

    // Create the dialog for updating each goal
    fun openGoalEditor(exerciseId: Long) {
        val strength = _uiState.value.goals.firstOrNull { it.exercise.id == exerciseId }
        val cardio = _uiState.value.cardioGoals.firstOrNull { it.exercise.id == exerciseId }
        val exercise = strength?.exercise ?: cardio?.exercise ?: return
        _uiState.update {
            it.copy(
                editor = GoalEditorState(
                    exerciseId = exerciseId,
                    exerciseName = exercise.name,
                    input = if (strength != null) exercise.goalKg?.let(weightsUnit::formatKilograms).orEmpty()
                        else exercise.cardioGoalDistanceMeters?.let(weightsUnit::formatMeters).orEmpty(),
                    type = if (strength != null) WorkoutType.STRENGTH else WorkoutType.CARDIO,
                    durationMinutes = exercise.cardioGoalDurationSeconds
                        ?.let { (it / 60).toString().padStart(2, '0') } ?: "00",
                    durationSeconds = exercise.cardioGoalDurationSeconds
                        ?.let { (it % 60).toString().padStart(2, '0') } ?: "00",
                ),
            )
        }
    }

    // Update the input of a goal
    fun updateGoalInput(value: String) {
        _uiState.update { state ->
            state.copy(editor = state.editor?.copy(
                input = value, errorMessage = null, distanceError = null,
            ))
        }
    }

    fun updateGoalMinutes(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            val normalized = normalizeDurationPart(editor.durationMinutes, value) ?: return@update state
            state.copy(editor = editor.copy(
                durationMinutes = normalized, errorMessage = null, timeError = null,
            ))
        }
    }

    fun updateGoalSeconds(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            val normalized = normalizeDurationPart(editor.durationSeconds, value, 2) ?: return@update state
            state.copy(editor = editor.copy(
                durationSeconds = normalized, errorMessage = null, timeError = null,
            ))
        }
    }

    // Close the window which edits a goal
    fun dismissGoalEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    // Save an updated goal value
    fun saveGoal() {
        val editor = _uiState.value.editor ?: return
        if (editor.type == WorkoutType.CARDIO) {
            val distance = editor.input.toDoubleOrNull()
            val duration = CardioEntryDraft(editor.durationMinutes, editor.durationSeconds).durationSecondsOrNull()
            val clearing = editor.input.isBlank() && duration == 0L
            val invalidDistance = !clearing && (distance == null || !distance.isFinite() || distance <= 0.0)
            val invalidTime = !clearing && (duration == null || duration <= 0)
            if (invalidDistance || invalidTime) {
                _uiState.update { it.copy(editor = editor.copy(
                    errorMessage = null,
                    distanceError = if (invalidDistance) "Enter positive distance" else null,
                    timeError = if (invalidTime) "Enter positive time" else null,
                )) }
                return
            }
            viewModelScope.launch {
                runCatching { goalRepository.updateCardioGoal(
                    editor.exerciseId,
                    if (clearing) null else weightsUnit.toMeters(requireNotNull(distance)),
                    if (clearing) null else duration,
                ) }.onSuccess { _uiState.update { it.copy(editor = null) } }
                    .onFailure { error -> _uiState.update { it.copy(editor = editor.copy(errorMessage = error.userMessage())) } }
            }
            return
        }
        val goal = if (editor.input.isBlank()) null else editor.input.toDoubleOrNull()
        if (goal != null && (!goal.isFinite() || goal <= 0.0) || goal == null && editor.input.isNotBlank()) {
            _uiState.update { it.copy(editor = editor.copy(errorMessage = "Enter a positive number, or leave it blank.")) }
            return
        }
        viewModelScope.launch {
            // Keep goals stored as kilograms regardless of the selected display unit
            runCatching {
                goalRepository.updateGoal(
                    editor.exerciseId,
                    goal?.let { WeightsUnit.METRIC.format(weightsUnit.toKilograms(it)).toDouble() },
                )
            }
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

    private fun normalizeDurationPart(current: String, changed: String, maxLength: Int? = null): String? {
        if (changed.any { !it.isDigit() }) return null
        val withoutDefault = if (current == "00" && changed.startsWith("00") && changed.length > 2) {
            changed.drop(2)
        } else changed
        return withoutDefault.takeIf { maxLength == null || it.length <= maxLength }
    }
}
