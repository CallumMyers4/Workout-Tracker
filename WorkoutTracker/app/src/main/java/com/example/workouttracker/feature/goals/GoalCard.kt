package com.example.workouttracker.feature.goals

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.CardioProgress
import com.example.workouttracker.core.model.formatDuration
import com.example.workouttracker.ui.theme.ActionButton
import com.example.workouttracker.ui.theme.GenericCard
import java.text.NumberFormat
import kotlin.math.roundToLong

@Composable
// Create a new card for a goal
fun GoalCard(
    progress: ExerciseProgress,
    weightsUnit: WeightsUnit = WeightsUnit.METRIC,
    onUpdateGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericCard(
        title = progress.exercise.name,
        modifier = modifier.fillMaxWidth(),
    ) {
        HorizontalDivider(
            thickness = 2.dp
        )
        // Create a row for goal
        DataRow(
            name = "Goal: ",
            data = progress.exercise.goalKg?.asWeight(weightsUnit) ?: "Not set"
        )
        HorizontalDivider(
            thickness = 2.dp
        )
        // Create a row for best set
        DataRow(
            name = "Best: ",
            data = progress.bestSet?.let { "${it.reps}x${it.weightKg.asWeight(weightsUnit)}" }
                    ?: "No sets found"
        )
        HorizontalDivider(
            thickness = 2.dp
        )
        // Create progress components only if there is a goal set
        if (progress.exercise.goalKg != null) {
            DataRow(
                name = "Progress: ",
                data = progress.percentage
                    ?.let { PERCENT_FORMAT.format(it) + "%" }
                    ?: "No sets found"
            )
            if (progress.percentage != null)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { progress.percentage.toFloat() / 100f },
                        Modifier.weight(2f)
                    )
                    Icon(
                        painter = painterResource(R.drawable.icon_medal),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            HorizontalDivider(
                thickness = 2.dp
            )
        }
        // Create a button to update the goal
        ActionButton(
            onClick = onUpdateGoal,
            text = "Update Goal",
            onCard = true
        )
    }
}

// Create a generic quick row with bold start and normal text
@Composable
fun DataRow(
    name: String,   // The bold bit at the front
    data: String,   // The normal unweighted text
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = data,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Normal
        )
    }
}

// Return weight as formatted string
private fun Double.asWeight(unit: WeightsUnit): String =
    unit.formatKilograms(this) + unit.symbol

// Return final progress percent formatted
private val PERCENT_FORMAT = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}

@Composable
fun CardioGoalCard(
    progress: CardioProgress,
    unit: WeightsUnit,
    onUpdateGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericCard(title = progress.exercise.name, modifier = modifier) {
        val goalDistance = progress.exercise.cardioGoalDistanceMeters
        val goalDuration = progress.exercise.cardioGoalDurationSeconds
        HorizontalDivider(thickness = 2.dp)
        DataRow("Goal: ", if (goalDistance != null && goalDuration != null) {
            formatPace(goalDuration.toDouble() / goalDistance, unit)
        } else "Not set")
        HorizontalDivider(thickness = 2.dp)
        DataRow("Latest: ", progress.latest?.let { entry ->
            entry.distanceMeters?.let { distance ->
                "${unit.formatMeters(distance)} ${unit.distanceSymbol} in ${entry.durationSeconds.formatDuration()}"
            }
        } ?: "No distance recorded")
        HorizontalDivider(thickness = 2.dp)
        DataRow("Longest Distance: ", progress.longestDistanceMeters
            ?.let { "${unit.formatMeters(it)} ${unit.distanceSymbol}" } ?: "No distance recorded")
        HorizontalDivider(thickness = 2.dp)
        DataRow("Best Pace: ", progress.fastestPaceSecondsPerMeter
            ?.let { formatPace(it, unit) } ?: "No distance recorded")
        HorizontalDivider(thickness = 2.dp)
        if (goalDistance != null && goalDuration != null) {
            DataRow(
                "Progress: ",
                progress.percentage?.let { PERCENT_FORMAT.format(it) + "%" }
                    ?: "No qualifying entry",
            )
            progress.percentage?.let { percentage ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { percentage.toFloat() / 100f },
                        modifier = Modifier.weight(2f),
                    )
                    Icon(
                        painter = painterResource(R.drawable.icon_medal),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            HorizontalDivider(thickness = 2.dp)
        }
        ActionButton(onClick = onUpdateGoal, text = "Update Goal", onCard = true)
    }
}

private fun formatPace(secondsPerMeter: Double, unit: WeightsUnit): String {
    val unitDistanceMeters = if (unit == WeightsUnit.METRIC) 1000.0 else 1609.344
    return (secondsPerMeter * unitDistanceMeters).roundToLong().formatDuration() + "/${unit.distanceSymbol}"
}
