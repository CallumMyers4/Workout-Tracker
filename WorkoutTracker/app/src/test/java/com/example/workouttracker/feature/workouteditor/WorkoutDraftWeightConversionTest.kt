package com.example.workouttracker.feature.workouteditor

import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutExerciseDraft
import com.example.workouttracker.core.model.CardioEntryDraft
import com.example.workouttracker.core.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorkoutDraftWeightConversionTest {
    @Test
    fun unchangedImperialTextRetainsExactStoredKilograms() {
        val setKey = "existing-set"
        val stored = draftWithWeight(setKey, "3.7")
        val displayed = draftWithWeight(setKey, "8.16")

        val result = displayed.toKilogramsPreservingUnchanged(
            WeightsUnit.IMPERIAL,
            originalDisplay = displayed,
            originalKilograms = stored,
        )

        assertEquals("3.7", result.firstWeight())
    }

    @Test
    fun editedImperialTextIsConvertedToKilograms() {
        val setKey = "existing-set"
        val stored = draftWithWeight(setKey, "3.7")
        val displayed = draftWithWeight(setKey, "8.16")
        val edited = draftWithWeight(setKey, "10")

        val result = edited.toKilogramsPreservingUnchanged(
            WeightsUnit.IMPERIAL,
            originalDisplay = displayed,
            originalKilograms = stored,
        )

        assertNotEquals("3.7", result.firstWeight())
        assertEquals("4.54", result.firstWeight())
    }

    @Test
    fun imperialCardioDistanceIsStoredAsMeters() {
        val draft = WorkoutDraft(
            type = WorkoutType.CARDIO,
            exercises = listOf(WorkoutExerciseDraft(
                editorKey = "cardio-exercise",
                cardioEntry = CardioEntryDraft("30", "00", "3.1"),
            )),
        )
        val result = draft.toKilogramsPreservingUnchanged(
            WeightsUnit.IMPERIAL, originalDisplay = null, originalKilograms = null,
        )
        assertEquals("4988.97", result.exercises.single().cardioEntry.distanceMeters)
    }

    @Test
    fun unchangedCardioDistanceRetainsExactStoredMeters() {
        val stored = WorkoutDraft(type = WorkoutType.CARDIO, exercises = listOf(WorkoutExerciseDraft(
            editorKey = "cardio-exercise", cardioEntry = CardioEntryDraft("30", "00", "5000.123456"),
        )))
        val displayed = stored.copy(exercises = listOf(stored.exercises.single().copy(
            cardioEntry = CardioEntryDraft("30", "00", "5"),
        )))
        val result = displayed.toKilogramsPreservingUnchanged(
            WeightsUnit.METRIC, originalDisplay = displayed, originalKilograms = stored,
        )
        assertEquals("5000.123456", result.exercises.single().cardioEntry.distanceMeters)
    }

    private fun draftWithWeight(setKey: String, weight: String) = WorkoutDraft(
        exercises = listOf(
            WorkoutExerciseDraft(
                editorKey = "exercise",
                sets = listOf(ExerciseSetDraft(editorKey = setKey, reps = "1", weightKg = weight)),
            ),
        ),
    )

    private fun WorkoutDraft.firstWeight(): String = exercises.first().sets.first().weightKg
}
