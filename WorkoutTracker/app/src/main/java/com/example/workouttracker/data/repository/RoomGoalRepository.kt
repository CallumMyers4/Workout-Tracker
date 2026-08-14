package com.example.workouttracker.data.repository

import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.data.local.WorkoutDatabase
import com.example.workouttracker.domain.repository.GoalRepository
import com.example.workouttracker.domain.service.ProgressCalculator
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import com.example.workouttracker.core.model.ExerciseSet
import com.example.workouttracker.data.mapper.toDomain
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

// Calculate and update exercise goal progress using Room data
class RoomGoalRepository(
    private val database: WorkoutDatabase,
    private val progressCalculator: ProgressCalculator,
) : GoalRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    // Observe the progress of every exercise in the catalog
    override fun observeProgress(): Flow<List<ExerciseProgress>> {
        return database.exerciseDao().observeCatalog().flatMapLatest { catalog ->
            if (catalog.isEmpty()) return@flatMapLatest flowOf(emptyList())
            combine(catalog.map { database.setDao().observeForCatalogExercise(it.id) }) { histories ->
                catalog.mapIndexed { index, entity ->
                    val history = histories[index].map { it.toDomain() }
                    val best = progressCalculator.findBestSet(history)
                    ExerciseProgress(
                        exercise = entity.toDomain(),
                        bestSet = best,
                        percentage = progressCalculator.calculatePercentage(best, entity.goalKg),
                    )
                }
            }
        }
    }

    // Update the goal of a given exercise
    override suspend fun updateGoal(exerciseId: Long, goalKg: Double?) {
        require(goalKg == null || (goalKg.isFinite() && goalKg > 0.0)) {
            "Goal must be a positive number."
        }
        database.withTransaction {
            val exercise = requireNotNull(database.exerciseDao().getCatalogExercise(exerciseId)) {
                "Exercise no longer exists."
            }
            database.exerciseDao().updateCatalogExercise(exercise.copy(goalKg = goalKg))
        }
    }
}
