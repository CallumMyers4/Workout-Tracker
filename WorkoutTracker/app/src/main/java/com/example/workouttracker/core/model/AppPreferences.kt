package com.example.workouttracker.core.model

// User preferences that should remain after an app restart
data class AppPreferences(
    val darkTheme: Boolean = false, // Style theme
    val searchText: String = "",    // Search workouts on home
    val filter: WorkoutFilter = WorkoutFilter.ALL_TIME, // Date filter for home
    val sort: WorkoutSort = WorkoutSort.NEWEST, // Ordering for home
    val grouping: WorkoutGrouping = WorkoutGrouping.NONE,   // How to group home page
)
