package com.example.workouttracker.feature.goals

import com.example.workouttracker.core.model.ExerciseProgress

// Current state of the goals page
data class GoalsUiState(
    val goals: List<ExerciseProgress> = emptyList(),
    val editor: GoalEditorState? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

// Current state of a window allowing user to update a goal
data class GoalEditorState(
    val exerciseId: Long,
    val exerciseName: String,
    val input: String,
    val errorMessage: String? = null,
)
