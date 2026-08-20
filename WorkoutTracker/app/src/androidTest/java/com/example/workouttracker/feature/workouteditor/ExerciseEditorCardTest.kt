package com.example.workouttracker.feature.workouteditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.WorkoutExerciseDraft
import org.junit.Rule
import org.junit.Test

// Check exercise editor cards using the Compose interface test runner
class ExerciseEditorCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    // Confirm that picker search displays only matching exercises
    fun pickerSearchFiltersVerticalExerciseMenu() {
        composeRule.setContent {
            MaterialTheme {
                ExerciseEditorCard(
                    exercise = WorkoutExerciseDraft(),
                    exerciseIndex = 0,
                    catalog = listOf(
                        CatalogExercise(1, "Bench Press"),
                        CatalogExercise(2, "Deadlift"),
                    ),
                    onSelected = {},
                    onCreateExercise = {},
                    onOpenNote = {},
                    onToggle = {},
                    onRemove = {},
                    onAddSet = {},
                    onSetChanged = { _, _, _ -> },
                    onRemoveSet = {},
                )
            }
        }

        composeRule.onNodeWithText("Select exercise").performClick()
        composeRule.onNodeWithText("Search exercises").performTextInput("dead")
        composeRule.onAllNodesWithText("Deadlift").assertCountEquals(1)
        composeRule.onAllNodesWithText("Bench Press").assertCountEquals(0)
    }

    @Test
    // Confirm that a hidden exercise displays its compact set summary
    fun collapsedCardShowsCompactSetSummary() {
        // Render the hidden state directly because the component receives its state from outside
        composeRule.setContent {
            MaterialTheme {
                ExerciseEditorCard(
                    exercise = WorkoutExerciseDraft(
                        catalogExerciseId = 1,
                        name = "Bench Press",
                        expanded = false,
                        sets = listOf(ExerciseSetDraft(reps = "5", weightKg = "80")),
                    ),
                    exerciseIndex = 0,
                    catalog = emptyList(),
                    onSelected = {},
                    onCreateExercise = {},
                    onOpenNote = {},
                    onToggle = {},
                    onRemove = {},
                    onAddSet = {},
                    onSetChanged = { _, _, _ -> },
                    onRemoveSet = {},
                )
            }
        }
        composeRule.onAllNodesWithText("1 set").assertCountEquals(1)
    }
}
