package com.example.workouttracker.feature.workoutdetail

import com.example.workouttracker.core.model.Workout

// Current state of the workout details page
data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val isLoading: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val errorMessage: String? = null,
)

// One-time action sent by the workout details page
sealed interface WorkoutDetailEvent {
    // Tell the app to leave the page after the workout has been deleted
    data object Deleted : WorkoutDetailEvent
}
