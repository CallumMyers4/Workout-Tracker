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

enum class WorkoutType { STRENGTH, CARDIO }
enum class ExerciseType { STRENGTH, CARDIO }

data class CardioEntry(
    val durationSeconds: Long,
    val distanceMeters: Double? = null,
)

// Exercise within a workout
data class WorkoutExercise(
    val id: Long = 0,
    val catalogExerciseId: Long,
    val name: String,
    val position: Int,
    val sets: List<ExerciseSet>,
    val cardioEntry: CardioEntry? = null,
)

// Full workout with all exercises and sets
data class Workout(
    val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val exercises: List<WorkoutExercise>,
    val type: WorkoutType = WorkoutType.STRENGTH,
)

// Short visual description of a workout for the home screen
data class WorkoutSummary(
    val id: Long,
    val name: String,
    val date: LocalDate?,
    val exerciseCount: Int,
    val exerciseNames: List<String>,
    val type: WorkoutType = WorkoutType.STRENGTH,
    val totalDurationSeconds: Long = 0,
    val totalDistanceMeters: Double = 0.0,
)

// Workout whilst it is being edited, so its text values have not yet been validated
// Keep numeric inputs as text so empty and unfinished values can still be displayed
data class WorkoutDraft(
    val workoutId: Long? = null,
    val name: String = "",
    val date: LocalDate = LocalDate.now(),
    val exercises: List<WorkoutExerciseDraft> = listOf(WorkoutExerciseDraft()),
    val type: WorkoutType = WorkoutType.STRENGTH,
) : Serializable

// Full exercise whilst being edited
data class WorkoutExerciseDraft(
    // Keep a separate editor key so Compose can preserve focus when rows are added or removed
    val editorKey: String = UUID.randomUUID().toString(),
    val catalogExerciseId: Long? = null,
    val name: String = "",
    val expanded: Boolean = true,
    val sets: List<ExerciseSetDraft> = listOf(ExerciseSetDraft()),
    val cardioEntry: CardioEntryDraft = CardioEntryDraft(),
) : Serializable

// Set within the exercise before being saved
data class ExerciseSetDraft(
    val editorKey: String = UUID.randomUUID().toString(),
    val reps: String = "",
    val weightKg: String = "",
) : Serializable

data class CardioEntryDraft(
    val minutes: String = "00",
    val seconds: String = "00",
    val distanceMeters: String = "",
) : Serializable

fun CardioEntryDraft.durationSecondsOrNull(): Long? {
    if (minutes.any { !it.isDigit() } || seconds.any { !it.isDigit() }) return null
    val minuteValue = minutes.ifBlank { "0" }.toLongOrNull() ?: return null
    val secondValue = seconds.ifBlank { "0" }.toLongOrNull() ?: return null
    if (secondValue !in 0..59) return null
    return runCatching {
        Math.addExact(Math.multiplyExact(minuteValue, 60), secondValue)
    }
        .getOrNull()
}

fun Long.formatDuration(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
