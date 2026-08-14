package com.example.workouttracker.domain.repository

import com.example.workouttracker.core.model.CatalogExercise
import kotlinx.coroutines.flow.Flow

// Requirements for classes that create an exercise in the catalog
interface ExerciseRepository {
    // Observe every exercise in the catalog
    fun observeCatalog(): Flow<List<CatalogExercise>>

    // Add a new catalog exercise and return its ID
    suspend fun addExercise(name: String): Long

    // Change the name of an existing exercise
    suspend fun renameExercise(exerciseId: Long, newName: String)

    // Delete an exercise which is not used by saved workout history
    suspend fun deleteExercise(exerciseId: Long)

    // Move history from one exercise into another
    suspend fun combineExercises(sourceExerciseId: Long, targetExerciseId: Long)

    // Add, replace, or clear an exercise note
    suspend fun setExerciseNote(exerciseId: Long, note: String?)

    // Observe the note shared by a workout name
    fun observeWorkoutNameNote(workoutName: String): Flow<String?>

    // Add, replace, or clear a workout name note
    suspend fun setWorkoutNameNote(workoutName: String, note: String?)
}
