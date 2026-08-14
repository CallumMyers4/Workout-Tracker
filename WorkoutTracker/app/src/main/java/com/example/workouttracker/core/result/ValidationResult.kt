package com.example.workouttracker.core.result

// Outcome of validating a saved workout, showing where issues were found if any
sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(
        val message: String,
        val field: WorkoutField,
        val exerciseIndex: Int? = null,
        val setIndex: Int? = null,
    ) : ValidationResult
}

// The field that failed to pass validation
enum class WorkoutField {
    NAME,
    DATE,
    EXERCISE,
    REPS,
    WEIGHT,
}
