package com.example.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "cardio_entries",
    primaryKeys = ["workoutExerciseId"],
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CardioEntryEntity(
    val workoutExerciseId: Long,
    val durationSeconds: Long,
    val distanceMeters: Double? = null,
)
