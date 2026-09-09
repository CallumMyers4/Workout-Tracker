package com.example.workouttracker.domain.repository

import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.core.model.CardioProgress
import kotlinx.coroutines.flow.Flow

// Requirements to implement a new goal
interface GoalRepository {
    // Observe goal progress for every catalog exercise
    fun observeProgress(): Flow<List<ExerciseProgress>>
    fun observeCardioProgress(): Flow<List<CardioProgress>>

    // Add, replace, or clear the goal for one exercise
    suspend fun updateGoal(exerciseId: Long, goalKg: Double?)
    suspend fun updateCardioGoal(exerciseId: Long, distanceMeters: Double?, durationSeconds: Long?)
}
