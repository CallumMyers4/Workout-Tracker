package com.example.workouttracker.domain.repository

import com.example.workouttracker.core.model.Workout
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.WorkoutSummary
import kotlinx.coroutines.flow.Flow

// Requirements to create a full workout class
interface WorkoutRepository {
    // Observe workout summaries matching the current browsing choices
    fun observeWorkoutSummaries(
        query: String,
        filter: WorkoutFilter,
        sort: WorkoutSort,
        grouping: WorkoutGrouping,
    ): Flow<List<WorkoutSummary>>

    // Observe one complete workout
    fun observeWorkout(workoutId: Long): Flow<Workout?>

    // Create or replace a workout and return its ID
    suspend fun saveWorkout(draft: WorkoutDraft): Long

    // Permanently delete one workout
    suspend fun deleteWorkout(workoutId: Long)
}
