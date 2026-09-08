package com.example.workouttracker.data.repository

import com.example.workouttracker.core.model.Workout
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.data.local.WorkoutDatabase
import com.example.workouttracker.data.local.entity.ExerciseSetEntity
import com.example.workouttracker.data.local.entity.WorkoutEntity
import com.example.workouttracker.data.local.entity.WorkoutExerciseEntity
import com.example.workouttracker.data.local.entity.CardioEntryEntity
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.durationSecondsOrNull
import com.example.workouttracker.data.mapper.toDomain
import com.example.workouttracker.data.mapper.toSummary
import com.example.workouttracker.domain.repository.WorkoutRepository
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate

// Load and save complete workouts using Room
class RoomWorkoutRepository(
    private val database: WorkoutDatabase,
) : WorkoutRepository {
    // Return a list of all workout summaries when given search conditions
    override fun observeWorkoutSummaries(
        query: String,
        filter: WorkoutFilter,
        sort: WorkoutSort,
        grouping: WorkoutGrouping,
    ): Flow<List<WorkoutSummary>> {
        val exerciseDao = database.exerciseDao()
        return combine(
            database.workoutDao().observeAll(),
            exerciseDao.observeAllWorkoutExercises(),
            exerciseDao.observeCatalog(),
            database.cardioDao().observeAll(),
        ) { workouts, entries, catalog, cardioEntries ->
            val names = catalog.associate { it.id to it.name }
            // Group once so mapping remains fast even with many saved workouts
            val entriesByWorkout = entries.groupBy { it.workoutId }
            val normalizedQuery = query.trim()
            val earliestDate = when (filter) {
                WorkoutFilter.ALL_TIME -> null
                WorkoutFilter.RECENT_30_DAYS -> LocalDate.now().minusDays(29)
                WorkoutFilter.RECENT_90_DAYS -> LocalDate.now().minusDays(89)
                WorkoutFilter.THIS_YEAR -> LocalDate.now().withDayOfYear(1)
            }
            workouts.map { workout ->
                val workoutExercises = entriesByWorkout[workout.id].orEmpty()
                    .map { entry -> entry.toDomain(
                        names[entry.catalogExerciseId] ?: "Unknown exercise",
                        emptyList(),
                        cardioEntries.firstOrNull { it.workoutExerciseId == entry.id },
                    ) }
                workout.toSummary(workoutExercises)
            }.filter { summary ->
                val matchesDate = earliestDate == null || summary.date?.let { it >= earliestDate } == true
                val matchesQuery = normalizedQuery.isEmpty() ||
                    summary.name.contains(normalizedQuery, ignoreCase = true) ||
                    summary.date?.toString()?.contains(normalizedQuery, ignoreCase = true) == true ||
                    summary.exerciseNames.any { it.contains(normalizedQuery, ignoreCase = true) }
                matchesDate && matchesQuery
            }.let { summaries ->
                when (sort) {
                    WorkoutSort.NEWEST -> summaries.sortedWith(
                        compareByDescending<WorkoutSummary> { it.date }.thenByDescending { it.id },
                    )
                    WorkoutSort.OLDEST -> summaries.sortedWith(
                        compareBy<WorkoutSummary> { it.date }.thenBy { it.id },
                    )
                }
            }.toList()
        }.flowOn(Dispatchers.Default)
    }

    // Return the full workout information of a given workout ID
    override fun observeWorkout(workoutId: Long): Flow<Workout?> {
        return combine(
            database.workoutDao().observeById(workoutId),
            database.exerciseDao().observeWorkoutExercises(workoutId),
            database.exerciseDao().observeCatalog(),
            database.setDao().observeForWorkout(workoutId),
            database.cardioDao().observeForWorkout(workoutId),
        ) { workout, entries, catalog, sets, cardioEntries ->
            workout?.toDomain(
                entries.map { entry ->
                    entry.toDomain(
                        catalog.firstOrNull { it.id == entry.catalogExerciseId }?.name
                            ?: "Unknown exercise",
                        sets.filter { it.workoutExerciseId == entry.id },
                        cardioEntries.firstOrNull { it.workoutExerciseId == entry.id },
                    )
                },
            )
        }.flowOn(Dispatchers.Default)
    }

    // Permanently save a given workout draft from workout editor
    override suspend fun saveWorkout(draft: WorkoutDraft): Long {
        return database.withTransaction {
            val workoutDao = database.workoutDao()
            val exerciseDao = database.exerciseDao()
            val parent = WorkoutEntity(
                id = draft.workoutId ?: 0,
                name = draft.name.trim(),
                date = draft.date,
                type = draft.type.name,
            )
            val workoutId = if (draft.workoutId == null) {
                workoutDao.insert(parent)
            } else {
                val existing = requireNotNull(workoutDao.getById(draft.workoutId)) { "Workout no longer exists." }
                require(existing.type == draft.type.name) { "An existing workout's type cannot be changed." }
                workoutDao.update(parent)
                draft.workoutId
            }

            // Delete old exercise rows so their sets are also removed by the foreign key
            exerciseDao.deleteWorkoutExercises(workoutId)
            draft.exercises.forEachIndexed { exercisePosition, draftExercise ->
                val catalogId = requireNotNull(draftExercise.catalogExerciseId) {
                    "Select an exercise before saving."
                }
                requireNotNull(exerciseDao.getCatalogExercise(catalogId)) {
                    "One of the selected exercises no longer exists."
                }.also { require(it.type == draft.type.name) { "Exercise type does not match workout type." } }
                val entryId = exerciseDao.insertWorkoutExercises(
                    listOf(
                        WorkoutExerciseEntity(
                            workoutId = workoutId,
                            catalogExerciseId = catalogId,
                            position = exercisePosition,
                        ),
                    ),
                ).single()
                if (draft.type == WorkoutType.STRENGTH) {
                    val setRows = draftExercise.sets.mapIndexed { setPosition, set ->
                        ExerciseSetEntity(
                            workoutExerciseId = entryId,
                            position = setPosition,
                            reps = requireNotNull(set.reps.toIntOrNull()) { "Reps must be a whole number." },
                            weightKg = requireNotNull(set.weightKg.toDoubleOrNull()) { "Weight must be a number." },
                        )
                    }
                    database.setDao().insertAll(setRows)
                } else {
                    val cardio = draftExercise.cardioEntry
                    val duration = requireNotNull(cardio.durationSecondsOrNull()) {
                        "Minutes and seconds must be valid whole numbers."
                    }
                    require(duration > 0) { "Duration must be positive." }
                    val distance = if (cardio.distanceMeters.isBlank()) null else {
                        requireNotNull(cardio.distanceMeters.toDoubleOrNull()) { "Distance must be a number." }
                            .also { require(it.isFinite() && it > 0.0) { "Distance must be positive." } }
                    }
                    database.cardioDao().insert(CardioEntryEntity(
                        workoutExerciseId = entryId,
                        durationSeconds = duration,
                        distanceMeters = distance,
                    ))
                }
            }
            workoutId
        }
    }

    // Fully delete a workout from the database
    override suspend fun deleteWorkout(workoutId: Long) {
        database.withTransaction { database.workoutDao().deleteById(workoutId) }
    }
}
