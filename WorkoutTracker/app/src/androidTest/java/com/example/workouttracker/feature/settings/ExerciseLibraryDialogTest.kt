package com.example.workouttracker.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExerciseLibraryDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun libraryUsesSearchAndAddPopup() {
        var added: Pair<String, ExerciseType>? = null
        composeRule.setContent {
            MaterialTheme {
                ExerciseLibraryDialog(
                    exercises = listOf(
                        CatalogExercise(1, "Bench press"),
                        CatalogExercise(2, "Bike"),
                    ),
                    onAdd = { name, type -> added = name to type },
                    onRename = { _, _ -> }, onDelete = {}, onCombine = { _, _ -> }, onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Search exercises").performTextInput("bike")
        composeRule.onAllNodesWithText("Bike").assertCountEquals(1)
        composeRule.onAllNodesWithText("Bench press").assertCountEquals(0)

        composeRule.onNodeWithText("Add exercise").performClick()
        composeRule.onNodeWithText("Exercise name").performTextInput("Rowing")
        composeRule.onNodeWithText("Add").performClick()
        assertEquals("Rowing" to ExerciseType.STRENGTH, added)
    }
}
