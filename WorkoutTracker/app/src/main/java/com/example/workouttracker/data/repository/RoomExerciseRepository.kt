package com.example.workouttracker.data.repository

import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.data.local.WorkoutDatabase
import com.example.workouttracker.data.local.entity.CatalogExerciseEntity
import com.example.workouttracker.data.local.entity.WorkoutNameNoteEntity
import com.example.workouttracker.data.mapper.toDomain
import com.example.workouttracker.domain.repository.ExerciseRepository
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

// Manage the exercise catalog and shared notes using Room
class RoomExerciseRepository(
    private val database: WorkoutDatabase,
) : ExerciseRepository {
    // Observe the exercise catalog as models used by the rest of the app
    override fun observeCatalog(): Flow<List<CatalogExercise>> {
        return database.exerciseDao().observeCatalog().map { rows -> rows.map { it.toDomain() } }
    }

    // Create new exercise in the catalog, and validate the name is valid
    override suspend fun addExercise(name: String): Long {
        val cleanName = validateName(name)
        return database.withTransaction {
            require(database.exerciseDao().findCatalogExercise(cleanName) == null) {
                "An exercise named '$cleanName' already exists."
            }
            database.exerciseDao().insertCatalogExercise(CatalogExerciseEntity(name = cleanName))
        }
    }

    // Rename a current existing exercise within the catalog
    override suspend fun renameExercise(exerciseId: Long, newName: String) {
        val cleanName = validateName(newName)
        database.withTransaction {
            val current = requireExercise(exerciseId)
            val collision = database.exerciseDao().findCatalogExercise(cleanName)
            require(collision == null || collision.id == exerciseId) {
                "An exercise named '$cleanName' already exists."
            }
            // Workout history uses the catalog ID, so changing this row updates every screen
            database.exerciseDao().updateCatalogExercise(current.copy(name = cleanName))
        }
    }

    // Remove an unused catalog exercise without deleting workout history
    override suspend fun deleteExercise(exerciseId: Long) {
        database.withTransaction {
            requireExercise(exerciseId)
            require(database.exerciseDao().countWorkoutExerciseReferences(exerciseId) == 0) {
                "This exercise is used by saved workouts. Combine it with another exercise instead."
            }
            database.exerciseDao().deleteCatalogExercise(exerciseId)
        }
    }

    // Merge two exercises by moving source history into the target and deleting the source
    override suspend fun combineExercises(sourceExerciseId: Long, targetExerciseId: Long) {
        require(sourceExerciseId != targetExerciseId) { "Choose two different exercises." }
        database.withTransaction {
            val source = requireExercise(sourceExerciseId)
            val target = requireExercise(targetExerciseId)
            val mergedGoal = listOfNotNull(source.goalKg, target.goalKg).maxOrNull()
            val mergedNote = target.note?.takeIf { it.isNotBlank() }
                ?: source.note?.takeIf { it.isNotBlank() }
            database.exerciseDao().repointWorkoutExercises(sourceExerciseId, targetExerciseId)
            database.exerciseDao().updateCatalogExercise(
                target.copy(goalKg = mergedGoal, note = mergedNote),
            )
            database.exerciseDao().deleteCatalogExercise(sourceExerciseId)
        }
    }

    // Add a note linked to a specific exercise
    override suspend fun setExerciseNote(exerciseId: Long, note: String?) {
        database.withTransaction {
            val exercise = requireExercise(exerciseId)
            database.exerciseDao().updateCatalogExercise(
                exercise.copy(note = note?.trim()?.takeIf { it.isNotEmpty() }),
            )
        }
    }

    // View the note attached to a workout
    override fun observeWorkoutNameNote(workoutName: String): Flow<String?> =
        database.exerciseDao().observeWorkoutNameNote(workoutName.trim().lowercase(Locale.ROOT))

    // Add a note linked to a specific workout name
    override suspend fun setWorkoutNameNote(workoutName: String, note: String?) {
        val key = workoutName.trim().lowercase(Locale.ROOT)
        require(key.isNotEmpty()) { "Workout name cannot be blank." }
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanNote == null) {
            database.exerciseDao().deleteWorkoutNameNote(key)
        } else {
            database.exerciseDao().upsertWorkoutNameNote(WorkoutNameNoteEntity(key, cleanNote))
        }
    }

    // Return an exercise or report that it was deleted before the action completed
    private suspend fun requireExercise(id: Long): CatalogExerciseEntity =
        requireNotNull(database.exerciseDao().getCatalogExercise(id)) { "Exercise no longer exists." }

    // Check that an exercise name is present and a reasonable length
    private fun validateName(name: String): String {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Exercise name cannot be blank." }
        require(cleanName.length <= 120) { "Exercise names can contain at most 120 characters." }
        return cleanName
    }
}
