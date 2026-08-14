package com.example.workouttracker.data.local.dao

import com.example.workouttracker.data.local.entity.ExerciseSetEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
// Database queries for the sets belonging to workout exercises
interface SetDao {
    // Observe the full set history of one catalog exercise
    @Query(
        """SELECT exercise_sets.* FROM exercise_sets
           INNER JOIN workout_exercises ON workout_exercises.id = exercise_sets.workoutExerciseId
           WHERE workout_exercises.catalogExerciseId = :exerciseId
           ORDER BY exercise_sets.id ASC""",
    )
    fun observeForCatalogExercise(exerciseId: Long): Flow<List<ExerciseSetEntity>>

    // Observe all sets within one workout
    @Query(
        """SELECT exercise_sets.* FROM exercise_sets
           INNER JOIN workout_exercises ON workout_exercises.id = exercise_sets.workoutExerciseId
           WHERE workout_exercises.workoutId = :workoutId
           ORDER BY workout_exercises.position, exercise_sets.position""",
    )
    fun observeForWorkout(workoutId: Long): Flow<List<ExerciseSetEntity>>

    // Observe every saved set
    @Query("SELECT * FROM exercise_sets ORDER BY workoutExerciseId, position")
    fun observeAll(): Flow<List<ExerciseSetEntity>>

    // Insert or delete sets owned by a workout exercise
    @Insert
    suspend fun insertAll(sets: List<ExerciseSetEntity>)

    @Query("DELETE FROM exercise_sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun deleteForWorkoutExercise(workoutExerciseId: Long)
}
