package com.example.workouttracker.feature.workouteditor

import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutExerciseDraft
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
