package com.example.workouttracker.feature.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.ExerciseProgress
import java.math.BigDecimal
import java.text.NumberFormat

@Composable
// Create a new card for a goal
fun GoalCard(
    progress: ExerciseProgress,
    onUpdateGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(progress.exercise.name)
            Text("Goal: ${progress.exercise.goalKg?.asWeight() ?: "Not set"}")
            Text(
                progress.bestSet?.let { "Best: ${it.reps} × ${it.weightKg.asWeight()}" }
                    ?: "Best: 0 reps × 0 kg",
            )
            Text("Progress: ${progress.percentage?.let { PERCENT_FORMAT.format(it) + "%" } ?: "N/A"}")
            TextButton(
                onClick = onUpdateGoal,
                modifier = Modifier.semantics {
                    contentDescription = "Update goal for ${progress.exercise.name}"
                },
            ) { Text("Update goal") }
        }
    }
}

// Return weight as formatted string
private fun Double.asWeight(): String =
    BigDecimal.valueOf(this).stripTrailingZeros().toPlainString() + " kg"

// Return final progress percent formatted
private val PERCENT_FORMAT = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}
