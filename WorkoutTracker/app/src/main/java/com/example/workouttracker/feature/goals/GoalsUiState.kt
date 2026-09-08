package com.example.workouttracker.feature.goals

import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.core.model.CardioProgress
import com.example.workouttracker.core.model.WorkoutType

// Current state of the goals page
data class GoalsUiState(
    val goals: List<ExerciseProgress> = emptyList(),
    val cardioGoals: List<CardioProgress> = emptyList(),
    val selectedType: WorkoutType = WorkoutType.STRENGTH,
    val editor: GoalEditorState? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

// Current state of a window allowing user to update a goal
data class GoalEditorState(
    val exerciseId: Long,
    val exerciseName: String,
    val input: String,
    val type: WorkoutType = WorkoutType.STRENGTH,
    val durationMinutes: String = "00",
    val durationSeconds: String = "00",
    val errorMessage: String? = null,
    val distanceError: String? = null,
    val timeError: String? = null,
)
