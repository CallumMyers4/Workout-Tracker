package com.example.workouttracker.feature.goals

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.ui.theme.ActionButton
import com.example.workouttracker.ui.theme.GenericCard
import java.math.BigDecimal
import java.text.NumberFormat

@Composable
// Create a new card for a goal
fun GoalCard(
    progress: ExerciseProgress,
    onUpdateGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GenericCard(
        title = progress.exercise.name,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Create a row for goal
        DataRow(
            name = "Goal: ",
            data = progress.exercise.goalKg?.asWeight() ?: "Not set"
        )
        // Create a row for best set
        DataRow(
            name = "Best: ",
            data = progress.bestSet?.let { "${it.reps}x${it.weightKg.asWeight()}" }
                    ?: "No sets found"
        )
        // Create a row for progress % if goal is set
        if (progress.exercise.goalKg != null)
                DataRow(
                    name = "Progress: ",
                    data = progress.percentage
                        ?.let { PERCENT_FORMAT.format(it) + "%" }
                        ?: "No sets found"
                )
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
private fun Double.asWeight(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString() + "kg"

// Return final progress percent formatted
private val PERCENT_FORMAT = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}
