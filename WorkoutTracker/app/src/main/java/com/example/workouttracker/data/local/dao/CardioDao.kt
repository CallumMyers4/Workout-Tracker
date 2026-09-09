package com.example.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.workouttracker.data.local.entity.CardioEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CardioDao {
    @Query("SELECT * FROM cardio_entries ORDER BY workoutExerciseId")
    fun observeAll(): Flow<List<CardioEntryEntity>>

    @Query("""SELECT cardio_entries.* FROM cardio_entries
        INNER JOIN workout_exercises ON workout_exercises.id = cardio_entries.workoutExerciseId
        WHERE workout_exercises.workoutId = :workoutId ORDER BY workout_exercises.position""")
    fun observeForWorkout(workoutId: Long): Flow<List<CardioEntryEntity>>

    @Query("""SELECT cardio_entries.* FROM cardio_entries
        INNER JOIN workout_exercises ON workout_exercises.id = cardio_entries.workoutExerciseId
        WHERE workout_exercises.catalogExerciseId = :exerciseId ORDER BY cardio_entries.workoutExerciseId""")
    fun observeForCatalogExercise(exerciseId: Long): Flow<List<CardioEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CardioEntryEntity)
}
