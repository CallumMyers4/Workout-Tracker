package com.example.workouttracker.feature.home

import com.example.workouttracker.core.model.WorkoutSummary

data class HomeUiState(
    val streakDays: Int = 0,
    val workoutsThisWeek: Int = 0,
    val workoutsLast30Days: Int = 0,
    val strengthWorkouts: Int = 0,
    val cardioWorkouts: Int = 0,
    val recentWorkouts: List<WorkoutSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
