package com.example.workouttracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// An exercise within the catalog
@Entity(
    tableName = "catalog_exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class CatalogExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val goalKg: Double? = null,
    val note: String? = null,
)
