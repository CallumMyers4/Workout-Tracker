package com.example.workouttracker.data.repository

import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.data.local.WorkoutDatabase
import com.example.workouttracker.domain.repository.GoalRepository
import com.example.workouttracker.domain.service.ProgressCalculator
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import com.example.workouttracker.core.model.ExerciseSet
import com.example.workouttracker.core.model.ExerciseType
import com.example.workouttracker.core.model.CardioProgress
import com.example.workouttracker.core.model.CardioEntry
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
                catalog.mapIndexedNotNull { index, entity ->
                    if (entity.type != ExerciseType.STRENGTH.name) return@mapIndexedNotNull null
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

    override fun observeCardioProgress(): Flow<List<CardioProgress>> = combine(
        database.exerciseDao().observeCatalog(),
        database.exerciseDao().observeAllWorkoutExercises(),
        database.workoutDao().observeAll(),
        database.cardioDao().observeAll(),
    ) { catalog, workoutExercises, workouts, cardioEntries ->
        val workoutOrder = workouts.associate { it.id to (it.date.toEpochDay() to it.id) }
        val exerciseByEntry = workoutExercises.associateBy { it.id }
        catalog.filter { it.type == ExerciseType.CARDIO.name }.map { catalogExercise ->
            val history = cardioEntries.mapNotNull { row ->
                val workoutExercise = exerciseByEntry[row.workoutExerciseId]
                    ?.takeIf { it.catalogExerciseId == catalogExercise.id } ?: return@mapNotNull null
                Triple(row, workoutExercise, workoutOrder[workoutExercise.workoutId] ?: (Long.MIN_VALUE to Long.MIN_VALUE))
            }
            val latest = history.filter { (it.first.distanceMeters ?: 0.0) > 0.0 }
                .maxWithOrNull(compareBy<Triple<com.example.workouttracker.data.local.entity.CardioEntryEntity, com.example.workouttracker.data.local.entity.WorkoutExerciseEntity, Pair<Long, Long>>> { it.third.first }.thenBy { it.third.second })
                ?.first?.let { CardioEntry(it.durationSeconds, it.distanceMeters) }
            val distances = history.mapNotNull { it.first.distanceMeters }
            val paced = history.filter { (it.first.distanceMeters ?: 0.0) > 0.0 }
            val fastest = paced.minOfOrNull { it.first.durationSeconds / requireNotNull(it.first.distanceMeters) }
            val goalDistance = catalogExercise.cardioGoalDistanceMeters
            val goalDuration = catalogExercise.cardioGoalDurationSeconds
            val qualifying = if (goalDistance == null) null else paced
                .filter { requireNotNull(it.first.distanceMeters) >= goalDistance }
                .minByOrNull { it.first.durationSeconds / requireNotNull(it.first.distanceMeters) }
                ?.first
            val best = qualifying?.let { CardioEntry(it.durationSeconds, it.distanceMeters) }
            val percentage = if (goalDistance != null && goalDuration != null && qualifying != null) {
                val targetPace = goalDuration.toDouble() / goalDistance
                val actualPace = qualifying.durationSeconds / requireNotNull(qualifying.distanceMeters)
                targetPace / actualPace * 100.0
            } else null
            CardioProgress(
                exercise = catalogExercise.toDomain(), latest = latest,
                longestDistanceMeters = distances.maxOrNull(), fastestPaceSecondsPerMeter = fastest,
                bestQualifying = best, percentage = percentage,
            )
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

    override suspend fun updateCardioGoal(exerciseId: Long, distanceMeters: Double?, durationSeconds: Long?) {
        require((distanceMeters == null) == (durationSeconds == null)) { "Enter both distance and duration, or clear both." }
        require(distanceMeters == null || (distanceMeters.isFinite() && distanceMeters > 0.0 && requireNotNull(durationSeconds) > 0)) {
            "Cardio goal values must be positive."
        }
        database.withTransaction {
            val exercise = requireNotNull(database.exerciseDao().getCatalogExercise(exerciseId)) { "Exercise no longer exists." }
            require(exercise.type == ExerciseType.CARDIO.name) { "Cardio goals can only be set for cardio exercises." }
            database.exerciseDao().updateCatalogExercise(exercise.copy(
                cardioGoalDistanceMeters = distanceMeters,
                cardioGoalDurationSeconds = durationSeconds,
            ))
        }
    }
}
