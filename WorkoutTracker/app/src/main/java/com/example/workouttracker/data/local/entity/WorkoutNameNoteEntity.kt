package com.example.workouttracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

// The notes attached to a specific workout
@Entity(tableName = "workout_name_notes", primaryKeys = ["workoutName"])
data class WorkoutNameNoteEntity(
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val workoutName: String,
    val note: String,
)
