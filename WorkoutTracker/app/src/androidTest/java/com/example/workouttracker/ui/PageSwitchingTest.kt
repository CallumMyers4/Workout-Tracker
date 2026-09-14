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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
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

        openTab("History")
        homeSearchField().assertIsDisplayed()

        openTab("New workout")
        composeRule.onNodeWithText("Strength").assertIsDisplayed()
        composeRule.onNodeWithText("Cardio").assertIsDisplayed()
    }

    @Test
    fun unfinishedLogDraftSurvivesSwitchingThroughEveryOtherTab() {
        showApp()
        openTab("New workout")
        composeRule.onNodeWithText("Strength").performClick()
        workoutNameField().performTextReplacement("Unfinished push day")

        listOf("Home", "History", "Progress", "Settings").forEach { destination ->
            openTab(destination)
            openTab("New workout")
            workoutNameField().assertTextContains("Unfinished push day", substring = true)
        }
    }

    @Test
    fun historyAlwaysReturnsToListAfterLeavingWorkoutDetails() {
        seedWorkout()
        showApp()
        openTab("History")
        composeRule.onNodeWithText("Existing workout").performClick()
        composeRule.onNodeWithText("Edit workout").assertIsDisplayed()

        openTab("Settings")
        openTab("History")

        homeSearchField().assertIsDisplayed()
        composeRule.onAllNodesWithText("Edit workout").assertCountEquals(0)
    }

    @Test
    fun cleanExistingEditExitsWithoutPromptAndHomeReturnsToList() {
        seedWorkout()
        showApp()
        openTab("History")
        openExistingWorkoutEditor()

        openTab("Progress")

        composeRule.onAllNodesWithText("Discard changes?").assertCountEquals(0)
        composeRule.onNodeWithText("Add exercises in Settings to create goals.").assertIsDisplayed()
        openTab("History")
        homeSearchField().assertIsDisplayed()
    }

    @Test
    fun keepEditingCancelsEveryDirtyEditTabSwitch() {
        seedWorkout()
        showApp()
        openTab("History")
        openExistingWorkoutEditor()
        editWorkoutName("Changed workout")

        listOf("New workout", "Progress", "Settings").forEach { destination ->
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
        openTab("New workout")
        composeRule.onNodeWithText("Strength").performClick()
        workoutNameField().performTextReplacement("Retained log draft")
        openTab("History")
        openExistingWorkoutEditor()
        editWorkoutName("Changed workout")

        openTab("New workout")
        composeRule.onNodeWithText("Keep editing").performClick()
        changedWorkoutNameField().assertTextContains("Changed workout", substring = true)

        openTab("New workout")
        composeRule.onNodeWithText("Discard").performClick()
        workoutNameField().assertTextContains("Retained log draft", substring = true)
    }

    @Test
    fun discardingDirtyEditOpensRequestedTabThenHomeList() {
        seedWorkout()
        showApp()
        openTab("History")

        listOf("New workout", "Progress", "Settings").forEach { destination ->
            openExistingWorkoutEditor()
            editWorkoutName("Discard me $destination")
            openTab(destination)
            composeRule.onNodeWithText("Discard").performClick()
            assertBasePage(destination)

            openTab("History")
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
        composeRule.onNodeWithText("Last 30 days").assertIsDisplayed()
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
        // The app intentionally hides primary navigation while the IME is visible.
        Espresso.closeSoftKeyboard()
        composeRule.waitForIdle()
        if (label == "New workout") {
            composeRule.onNodeWithContentDescription(label).performClick()
        } else {
            composeRule.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
        }
    }

    private fun assertBasePage(label: String) {
        when (label) {
            "New workout" -> composeRule.onNodeWithText("Strength").assertIsDisplayed()
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
