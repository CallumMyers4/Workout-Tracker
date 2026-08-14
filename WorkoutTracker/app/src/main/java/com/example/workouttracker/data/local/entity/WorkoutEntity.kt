package com.example.workouttracker.data.local.entity

import java.time.LocalDate
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// A saved workout whose child exercises are deleted with it
@Entity(
    tableName = "workouts",
    indices = [Index("date"), Index("name")],
)
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val date: LocalDate,
)
