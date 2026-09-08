package com.example.workouttracker.feature.workouteditor

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.workouttracker.core.model.WorkoutType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LogTypeChooserTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun strengthAndCardioFillEqualStackedAreasAndAreAccessible() {
        var selected: WorkoutType? = null
        composeRule.setContent { MaterialTheme { LogTypeChooser(onTypeSelected = { selected = it }) } }

        composeRule.onNodeWithContentDescription("Strength log").fetchSemanticsNode()
        composeRule.onNodeWithContentDescription("Cardio log").fetchSemanticsNode()
        val strength = composeRule.onNodeWithTag("strength_log_button").fetchSemanticsNode().boundsInRoot
        val cardio = composeRule.onNodeWithTag("cardio_log_button").fetchSemanticsNode().boundsInRoot
        assertTrue(strength.center.y < cardio.center.y)
        assertEquals(strength.height, cardio.height, 1f)

        composeRule.onNodeWithTag("cardio_log_button").performClick()
        assertEquals(WorkoutType.CARDIO, selected)
    }
}
