package com.example.workouttracker.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workouttracker.AppContainer
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.data.backup.GoogleAuthorizationGateway
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
// Exercise primary navigation through the real app graph and ViewModels.
class PageSwitchingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var container: AppContainer

    @Before
    fun createCleanAppContainer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        container = AppContainer(context, UnavailableAuthorizationGateway)
        runBlocking {
            container.database.clearAllTables()
            container.preferencesRepository.reset()
        }
    }

    @Test
    fun everyPrimaryButtonOpensItsBasePage() {
        showApp()

        openTab("Progress")
        composeRule.onNodeWithText("Add exercises in Settings to create goals.").assertIsDisplayed()

        openTab("Settings")
        composeRule.onNodeWithText("Manage exercise library").assertIsDisplayed()

        openTab("Home")
        homeSearchField().assertIsDisplayed()

        openTab("Log")
        workoutNameField().assertIsDisplayed()
    }

    @Test
    fun unfinishedLogDraftSurvivesSwitchingThroughEveryOtherTab() {
        showApp()
        openTab("Log")
        workoutNameField().performTextReplacement("Unfinished push day")

        listOf("Home", "Progress", "Settings").forEach { destination ->
            openTab(destination)
            openTab("Log")
            workoutNameField().assertTextContains("Unfinished push day", substring = true)
        }
    }

    @Test
    fun homeAlwaysReturnsToListAfterLeavingWorkoutDetails() {
        seedWorkout()
        showApp()
        composeRule.onNodeWithText("Existing workout").performClick()
        composeRule.onNodeWithText("Edit workout").assertIsDisplayed()

        openTab("Settings")
        openTab("Home")

        homeSearchField().assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit workout").assertCountEquals(0)
    }

    @Test
    fun cleanExistingEditExitsWithoutPromptAndHomeReturnsToList() {
        seedWorkout()
        showApp()
        openExistingWorkoutEditor()

        openTab("Progress")

        composeRule.onAllNodesWithText("Discard changes?").assertCountEquals(0)
        composeRule.onNodeWithText("Add exercises in Settings to create goals.").assertIsDisplayed()
        openTab("Home")
        homeSearchField().assertIsDisplayed()
    }

    @Test
    fun keepEditingCancelsEveryDirtyEditTabSwitch() {
        seedWorkout()
        showApp()
        openExistingWorkoutEditor()
        editWorkoutName("Changed workout")

        listOf("Log", "Progress", "Settings").forEach { destination ->
            openTab(destination)
            composeRule.onNodeWithText("Discard changes?").assertIsDisplayed()
            composeRule.onNodeWithText("Keep editing").performClick()
            composeRule.onNodeWithText("Edit workout").assertIsDisplayed()
            changedWorkoutNameField().assertTextContains("Changed workout", substring = true)
        }
    }

    @Test
    fun dirtyEditPromptDoesNotOverwriteAnExistingLogDraft() {
        seedWorkout()
        showApp()
        openTab("Log")
        workoutNameField().performTextReplacement("Retained log draft")
        openTab("Home")
        openExistingWorkoutEditor()
        editWorkoutName("Changed workout")

        openTab("Log")
        composeRule.onNodeWithText("Keep editing").performClick()
        changedWorkoutNameField().assertTextContains("Changed workout", substring = true)

        openTab("Log")
        composeRule.onNodeWithText("Discard").performClick()
        workoutNameField().assertTextContains("Retained log draft", substring = true)
    }

    @Test
    fun discardingDirtyEditOpensRequestedTabThenHomeList() {
        seedWorkout()
        showApp()

        listOf("Log", "Progress", "Settings").forEach { destination ->
            openExistingWorkoutEditor()
            editWorkoutName("Discard me $destination")
            openTab(destination)
            composeRule.onNodeWithText("Discard").performClick()
            assertBasePage(destination)

            openTab("Home")
            homeSearchField().assertIsDisplayed()
            composeRule.onAllNodesWithText("Edit workout").assertCountEquals(0)
        }
    }

    private fun showApp() {
        composeRule.setContent {
            MaterialTheme {
                WorkoutTrackerApp(container)
            }
        }
        homeSearchField().assertIsDisplayed()
    }

    private fun seedWorkout(): Long = runBlocking {
        container.workoutRepository.saveWorkout(
            WorkoutDraft(name = "Existing workout", exercises = emptyList()),
        )
    }

    private fun openExistingWorkoutEditor() {
        composeRule.onNodeWithText("Existing workout").performClick()
        composeRule.onNodeWithText("Edit workout").performClick()
        composeRule.onNodeWithText("Edit workout").assertIsDisplayed()
    }

    private fun editWorkoutName(name: String) {
        composeRule.onNode(hasText("Existing workout") and hasSetTextAction())
            .performTextReplacement(name)
    }

    private fun homeSearchField() =
        composeRule.onNode(hasText("Search workouts") and hasSetTextAction())

    private fun workoutNameField() =
        composeRule.onNode(hasText("Workout name") and hasSetTextAction())

    private fun changedWorkoutNameField() =
        composeRule.onNode(hasText("Changed workout") and hasSetTextAction())

    private fun openTab(label: String) {
        composeRule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
    }

    private fun assertBasePage(label: String) {
        when (label) {
            "Log" -> workoutNameField().assertIsDisplayed()
            "Progress" -> composeRule.onNodeWithText("Add exercises in Settings to create goals.")
                .assertIsDisplayed()
            "Settings" -> composeRule.onNodeWithText("Manage exercise library").assertIsDisplayed()
        }
    }
}

private object UnavailableAuthorizationGateway : GoogleAuthorizationGateway {
    override fun isAvailable() = false
    override suspend fun authorize() = Unit
    override suspend fun revoke() = Unit
    override suspend fun hasAuthorization() = false
    override suspend fun accessToken() = error("Authorization is unavailable in navigation tests.")
}
