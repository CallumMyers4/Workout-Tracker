package com.example.workouttracker.feature.workoutlist

import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.WorkoutSummary

// Current state of the workout list page
data class WorkoutListUiState(
    val workouts: List<WorkoutSummary> = emptyList(),
    val searchText: String = "",
    val filter: WorkoutFilter = WorkoutFilter.ALL_TIME,
    val sort: WorkoutSort = WorkoutSort.NEWEST,
    val grouping: WorkoutGrouping = WorkoutGrouping.NONE,
    val collapsedGroups: Set<String> = emptySet(),
    val loadedItemCount: Int = 0,
    val totalItemCount: Int = 0,
    val hasAnyWorkouts: Boolean = false,
    val loadedDateWindowDays: Int = 14,
    val loadedUnknownDateCount: Int = 0,
    val sections: List<WorkoutSection> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

// One displayed group of workouts with a stable key and user-friendly title
data class WorkoutSection(
    val key: String,
    val title: String,
    val workouts: List<WorkoutSummary>,
)
