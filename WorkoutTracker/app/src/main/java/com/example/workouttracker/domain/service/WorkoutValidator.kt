package com.example.workouttracker.domain.service

import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.result.ValidationResult
import com.example.workouttracker.core.result.WorkoutField
import java.time.LocalDate
import java.math.BigDecimal

// Validate each section of a workout draft before it is saved
class WorkoutValidator {
    // Return the first validation problem or confirm that the complete draft is valid
    fun validate(draft: WorkoutDraft): ValidationResult {
        if (draft.name.isBlank()) {
            return ValidationResult.Invalid("Enter a workout name.", WorkoutField.NAME)
        }
        if (draft.name.trim().length > 120) {
            return ValidationResult.Invalid("Workout names can contain at most 120 characters.", WorkoutField.NAME)
        }
        val currentYear = LocalDate.now().year
        if (draft.date.year !in (currentYear - 20)..(currentYear + 5)) {
            return ValidationResult.Invalid(
                "Choose a date from ${currentYear - 20} through ${currentYear + 5}.",
                WorkoutField.DATE,
            )
        }
        if (draft.exercises.isEmpty()) {
            return ValidationResult.Invalid("Add at least one exercise.", WorkoutField.EXERCISE)
        }

        draft.exercises.forEachIndexed { exerciseIndex, exercise ->
            if (exercise.catalogExerciseId == null) {
                return ValidationResult.Invalid(
                    "Select an exercise.", WorkoutField.EXERCISE, exerciseIndex,
                )
            }
            if (exercise.sets.isEmpty()) {
                return ValidationResult.Invalid(
                    "Add at least one set.", WorkoutField.REPS, exerciseIndex, 0,
                )
            }
            exercise.sets.forEachIndexed { setIndex, set ->
                val reps = set.reps.toIntOrNull()
                if (reps == null || reps <= 0) {
                    return ValidationResult.Invalid(
                        "Reps must be a positive whole number.",
                        WorkoutField.REPS,
                        exerciseIndex,
                        setIndex,
                    )
                }
                val decimalWeight = set.weightKg.toBigDecimalOrNull()
                val weight = decimalWeight?.toDouble()
                val losesPrecision = decimalWeight != null && weight != null && weight.isFinite() &&
                    decimalWeight.stripTrailingZeros().compareTo(
                        BigDecimal.valueOf(weight).stripTrailingZeros(),
                    ) != 0
                if (weight == null || !weight.isFinite() || weight < 0.0 || losesPrecision) {
                    return ValidationResult.Invalid(
                        "Weight must be a non-negative decimal that can be stored accurately.",
                        WorkoutField.WEIGHT,
                        exerciseIndex,
                        setIndex,
                    )
                }
            }
        }
        return ValidationResult.Valid
    }
}
