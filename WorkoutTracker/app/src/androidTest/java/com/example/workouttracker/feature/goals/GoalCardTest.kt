package com.example.workouttracker.feature.goals

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.workouttracker.core.model.BestSet
import com.example.workouttracker.core.model.CardioEntry
import com.example.workouttracker.core.model.CardioProgress
import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.ExerciseType
import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.core.model.WeightsUnit
import org.junit.Rule
import org.junit.Test

// Check the goal card's conditional progress states using the Compose interface test runner
class GoalCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun noGoalHidesProgressStateAndIndicator() {
        showGoalCard(goalKg = null, bestSet = null, percentage = null)

        composeRule.onNodeWithText("Not set").assertIsDisplayed()
        composeRule.onNodeWithText("Progress: ").assertDoesNotExist()
        progressIndicators().assertCountEquals(0)
    }

    @Test
    fun goalWithoutHistoryShowsEmptyProgressStateAndHidesIndicator() {
        showGoalCard(goalKg = 100.0, bestSet = null, percentage = null)

        composeRule.onNodeWithText("Progress: ").assertIsDisplayed()
        composeRule.onAllNodesWithText("No sets found").assertCountEquals(2)
        progressIndicators().assertCountEquals(0)
    }

    @Test
    fun numericPercentageShowsIndicatorWithFractionalProgress() {
        showGoalCard(
            goalKg = 100.0,
            bestSet = BestSet(reps = 5, weightKg = 75.0),
            percentage = 75.0,
        )

        composeRule.onNodeWithText("75.0%").assertIsDisplayed()
        progressIndicator().assertRangeInfoEquals(
            ProgressBarRangeInfo(current = 0.75f, range = 0f..1f),
        )
    }

    @Test
    fun percentageAboveOneHundredShowsFullIndicatorWithoutChangingDisplayedValue() {
        showGoalCard(
            goalKg = 100.0,
            bestSet = BestSet(reps = 1, weightKg = 125.0),
            percentage = 125.0,
        )

        composeRule.onNodeWithText("125.0%").assertIsDisplayed()
        progressIndicator().assertRangeInfoEquals(
            ProgressBarRangeInfo(current = 1f, range = 0f..1f),
        )
    }

    @Test
    fun cardioCardShowsLatestDistancePaceGoalAndProgress() {
        composeRule.setContent {
            MaterialTheme {
                CardioGoalCard(
                    progress = CardioProgress(
                        exercise = CatalogExercise(
                            id = 2,
                            name = "Cycling",
                            type = ExerciseType.CARDIO,
                            cardioGoalDistanceMeters = 5_000.0,
                            cardioGoalDurationSeconds = 1_500,
                        ),
                        latest = CardioEntry(durationSeconds = 1_620, distanceMeters = 5_000.0),
                        longestDistanceMeters = 10_000.0,
                        fastestPaceSecondsPerMeter = 0.24,
                        percentage = 120.0,
                    ),
                    unit = WeightsUnit.METRIC,
                    onUpdateGoal = {},
                )
            }
        }

        composeRule.onNodeWithText("5 km in 27:00").assertIsDisplayed()
        composeRule.onNodeWithText("10 km").assertIsDisplayed()
        composeRule.onNodeWithText("4:00/km").assertIsDisplayed()
        composeRule.onNodeWithText("5:00/km").assertIsDisplayed()
        composeRule.onNodeWithText("120.0%").assertIsDisplayed()
        progressIndicator().assertRangeInfoEquals(
            ProgressBarRangeInfo(current = 1f, range = 0f..1f),
        )
    }

    @Test
    fun cardioCardWithoutGoalHidesProgressStateAndIndicator() {
        composeRule.setContent {
            MaterialTheme {
                CardioGoalCard(
                    progress = CardioProgress(
                        exercise = CatalogExercise(
                            id = 2,
                            name = "Cycling",
                            type = ExerciseType.CARDIO,
                        ),
                    ),
                    unit = WeightsUnit.METRIC,
                    onUpdateGoal = {},
                )
            }
        }

        composeRule.onNodeWithText("Progress: ").assertDoesNotExist()
        progressIndicators().assertCountEquals(0)
    }

    private fun showGoalCard(
        goalKg: Double?,
        bestSet: BestSet?,
        percentage: Double?,
    ) {
        composeRule.setContent {
            MaterialTheme {
                GoalCard(
                    progress = ExerciseProgress(
                        exercise = CatalogExercise(
                            id = 1,
                            name = "Bench Press",
                            goalKg = goalKg,
                        ),
                        bestSet = bestSet,
                        percentage = percentage,
                    ),
                    onUpdateGoal = {},
                )
            }
        }
    }

    private fun progressIndicator() = composeRule.onNode(PROGRESS_INDICATOR_MATCHER)

    private fun progressIndicators() = composeRule.onAllNodes(PROGRESS_INDICATOR_MATCHER)

    private companion object {
        val PROGRESS_INDICATOR_MATCHER =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    }
}
