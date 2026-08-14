package com.example.workouttracker.data.local.dao

import com.example.workouttracker.data.local.entity.CatalogExerciseEntity
import com.example.workouttracker.data.local.entity.WorkoutExerciseEntity
import com.example.workouttracker.data.local.entity.WorkoutNameNoteEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
// Database queries for catalog exercises, workout exercises, and shared workout notes
interface ExerciseDao {
    // Observe or retrieve exercises from the catalog
    @Query("SELECT * FROM catalog_exercises ORDER BY name COLLATE NOCASE ASC")
    fun observeCatalog(): Flow<List<CatalogExerciseEntity>>

    @Query("SELECT * FROM catalog_exercises ORDER BY name COLLATE NOCASE ASC")
    suspend fun getCatalog(): List<CatalogExerciseEntity>

    // Find, add, update, or delete individual catalog exercises
    @Query("SELECT * FROM catalog_exercises WHERE id = :exerciseId")
    suspend fun getCatalogExercise(exerciseId: Long): CatalogExerciseEntity?

    @Query("SELECT * FROM catalog_exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCatalogExercise(name: String): CatalogExerciseEntity?

    @Insert
    suspend fun insertCatalogExercise(exercise: CatalogExerciseEntity): Long

    @Update
    suspend fun updateCatalogExercise(exercise: CatalogExerciseEntity)

    @Query("DELETE FROM catalog_exercises WHERE id = :exerciseId")
    suspend fun deleteCatalogExercise(exerciseId: Long)

    @Query("SELECT COUNT(*) FROM workout_exercises WHERE catalogExerciseId = :exerciseId")
    suspend fun countWorkoutExerciseReferences(exerciseId: Long): Int

    // Add and observe the exercises contained in saved workouts
    @Insert
    suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>): List<Long>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    fun observeWorkoutExercises(workoutId: Long): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises ORDER BY workoutId, position")
    fun observeAllWorkoutExercises(): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_exercises WHERE workoutId = :workoutId ORDER BY position")
    suspend fun getWorkoutExercises(workoutId: Long): List<WorkoutExerciseEntity>

    // Move workout history between catalog exercises or remove a workout's exercises
    @Query("UPDATE workout_exercises SET catalogExerciseId = :targetId WHERE catalogExerciseId = :sourceId")
    suspend fun repointWorkoutExercises(sourceId: Long, targetId: Long)

    @Query("DELETE FROM workout_exercises WHERE workoutId = :workoutId")
    suspend fun deleteWorkoutExercises(workoutId: Long)

    // Add, observe, or remove the note shared by a workout name
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkoutNameNote(note: WorkoutNameNoteEntity)

    @Query("SELECT note FROM workout_name_notes WHERE workoutName = :workoutName COLLATE NOCASE")
    fun observeWorkoutNameNote(workoutName: String): Flow<String?>

    @Query("DELETE FROM workout_name_notes WHERE workoutName = :workoutName COLLATE NOCASE")
    suspend fun deleteWorkoutNameNote(workoutName: String)
}
