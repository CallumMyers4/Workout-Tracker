package com.example.workouttracker.domain.service

import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutExerciseDraft
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.CardioEntryDraft
import com.example.workouttracker.core.result.ValidationResult
import com.example.workouttracker.core.result.WorkoutField
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Check valid workout drafts and the location returned for each error
class WorkoutValidatorTest {
    private val validator = WorkoutValidator()

    @Test
    // Confirm that a complete draft passes validation
    fun validDraftPasses() {
        assertEquals(ValidationResult.Valid, validator.validate(validDraft()))
    }

    @Test
    // Confirm that workout name validation runs before later checks
    fun blankNameIsFirstError() {
        val result = validator.validate(validDraft().copy(name = " ")) as ValidationResult.Invalid
        assertEquals(WorkoutField.NAME, result.field)
    }

    @Test
    // Confirm that a missing exercise identifies its editor row
    fun missingExercisePointsToItsRow() {
        val result = validator.validate(
            validDraft().copy(exercises = listOf(WorkoutExerciseDraft())),
        ) as ValidationResult.Invalid
        assertEquals(WorkoutField.EXERCISE, result.field)
        assertEquals(0, result.exerciseIndex)
    }

    @Test
    // Confirm that invalid repetitions identify the exact set
    fun invalidSetPointsToExactSet() {
        val draft = validDraft().copy(
            exercises = listOf(
                validDraft().exercises.single().copy(
                    sets = listOf(
                        ExerciseSetDraft(reps = "5", weightKg = "20"),
                        ExerciseSetDraft(reps = "2.5", weightKg = "20"),
                    ),
                ),
            ),
        )
        val result = validator.validate(draft) as ValidationResult.Invalid
        assertEquals(WorkoutField.REPS, result.field)
        assertEquals(0, result.exerciseIndex)
        assertEquals(1, result.setIndex)
    }

    @Test
    // Confirm that weights which cannot be stored accurately are rejected
    fun weightThatCannotRoundTripIsRejected() {
        val set = ExerciseSetDraft(reps = "5", weightKg = "0.1234567890123456789")
        val draft = validDraft().copy(
            exercises = listOf(validDraft().exercises.single().copy(sets = listOf(set))),
        )
        val result = validator.validate(draft) as ValidationResult.Invalid
        assertEquals(WorkoutField.WEIGHT, result.field)
        assertTrue(result.message.contains("accurately"))
    }

    @Test
    fun validCardioDraftPassesWithOptionalDistance() {
        val draft = WorkoutDraft(
            name = "Morning ride",
            type = WorkoutType.CARDIO,
            exercises = listOf(WorkoutExerciseDraft(
                catalogExerciseId = 2,
                name = "Bike",
                cardioEntry = CardioEntryDraft(minutes = "62", seconds = "03", distanceMeters = "25.5"),
            )),
        )
        assertEquals(ValidationResult.Valid, validator.validate(draft))
        assertEquals(
            ValidationResult.Valid,
            validator.validate(draft.copy(exercises = listOf(draft.exercises.single().copy(
                cardioEntry = CardioEntryDraft(minutes = "30", seconds = "00"),
            )))),
        )
    }

    @Test
    fun cardioDurationAndDistanceAreValidated() {
        val base = WorkoutDraft(
            name = "Ride", type = WorkoutType.CARDIO,
            exercises = listOf(WorkoutExerciseDraft(
                catalogExerciseId = 2, name = "Bike",
                cardioEntry = CardioEntryDraft("12", "60", "5"),
            )),
        )
        assertEquals(WorkoutField.DURATION, (validator.validate(base) as ValidationResult.Invalid).field)
        val invalidDistance = base.copy(exercises = listOf(base.exercises.single().copy(
            cardioEntry = CardioEntryDraft("12", "30", "0"),
        )))
        assertEquals(WorkoutField.DISTANCE, (validator.validate(invalidDistance) as ValidationResult.Invalid).field)
    }

    // Create a complete draft which individual tests can alter
    private fun validDraft() = WorkoutDraft(
        name = "Push day",
        date = LocalDate.now(),
        exercises = listOf(
            WorkoutExerciseDraft(
                catalogExerciseId = 1,
                name = "Bench press",
                sets = listOf(ExerciseSetDraft(reps = "5", weightKg = "80.5")),
            ),
        ),
    )
}
