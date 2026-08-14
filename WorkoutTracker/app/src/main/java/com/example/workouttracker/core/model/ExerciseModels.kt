package com.example.workouttracker.core.model

// Metadata for each exercise created in the catalog on settings page
data class CatalogExercise(
    val id: Long,
    val name: String,
    val goalKg: Double? = null,
    val note: String? = null,
)

// Best recorded set for each exercise
data class BestSet(
    val reps: Int,
    val weightKg: Double,
)

// Progress towards hitting the goal of each exercise
data class ExerciseProgress(
    val exercise: CatalogExercise,
    val bestSet: BestSet?,
    val percentage: Double?,
)
