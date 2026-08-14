package com.example.workouttracker.core.model

import java.time.LocalDate
import java.util.UUID
import java.io.Serializable

// Single set within a workout
data class ExerciseSet(
    val id: Long = 0,
    val position: Int,
    val reps: Int,
    val weightKg: Double,
)

// Exercise within a workout
data class WorkoutExercise(
    val id: Long = 0,
    val catalogExerciseId: Long,
    val name: String,
    val position: Int,
    val sets: List<ExerciseSet>,
)

// Full workout with all exercises and sets
data class Workout(
    val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val exercises: List<WorkoutExercise>,
)

// Short visual description of a workout for the home screen
data class WorkoutSummary(
    val id: Long,
    val name: String,
    val date: LocalDate?,
    val exerciseCount: Int,
    val exerciseNames: List<String>,
)

// Workout whilst it is being edited, so its text values have not yet been validated
// Keep numeric inputs as text so empty and unfinished values can still be displayed
data class WorkoutDraft(
    val workoutId: Long? = null,
    val name: String = "",
    val date: LocalDate = LocalDate.now(),
    val exercises: List<WorkoutExerciseDraft> = listOf(WorkoutExerciseDraft()),
) : Serializable

// Full exercise whilst being edited
data class WorkoutExerciseDraft(
    // Keep a separate editor key so Compose can preserve focus when rows are added or removed
    val editorKey: String = UUID.randomUUID().toString(),
    val catalogExerciseId: Long? = null,
    val name: String = "",
    val expanded: Boolean = true,
    val sets: List<ExerciseSetDraft> = listOf(ExerciseSetDraft()),
) : Serializable

// Set within the exercise before being saved
data class ExerciseSetDraft(
    val editorKey: String = UUID.randomUUID().toString(),
    val reps: String = "",
    val weightKg: String = "",
) : Serializable
