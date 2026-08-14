package com.example.workouttracker.navigation

import kotlinx.serialization.Serializable

// Define every page which can be opened and the values it needs from navigation
@Serializable
sealed interface AppRoute {
    @Serializable
    data object WorkoutList : AppRoute
    @Serializable
    data class WorkoutDetail(val workoutId: Long) : AppRoute
    @Serializable
    data class WorkoutEditor(val workoutId: Long? = null) : AppRoute
    @Serializable
    data object Goals : AppRoute
    @Serializable
    data object Settings : AppRoute
}
