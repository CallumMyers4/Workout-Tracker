package com.example.workouttracker.data.local.dao

import com.example.workouttracker.data.local.entity.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
// Database queries for creating, observing, updating, and deleting workouts
interface WorkoutDao {
    // Observe all workouts in newest-first order
    @Query("SELECT * FROM workouts ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    // Observe or retrieve one workout using its database ID
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    fun observeById(workoutId: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getById(workoutId: Long): WorkoutEntity?

    // Insert, update, or delete a workout
    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteById(workoutId: Long)
}
