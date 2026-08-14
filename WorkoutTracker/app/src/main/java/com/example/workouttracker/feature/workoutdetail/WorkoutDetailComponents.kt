package com.example.workouttracker.feature.workoutdetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.WorkoutExercise
import java.math.BigDecimal

// Create a card showing an exercise and all of its saved sets
@Composable
fun ExerciseSummaryCard(
    exercise: WorkoutExercise,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp)) {
            Text(exercise.name)
            // Display sets using their original order in the workout
            exercise.sets.sortedBy { it.position }.forEachIndexed { index, set ->
                val weight = BigDecimal.valueOf(set.weightKg).stripTrailingZeros().toPlainString()
                Text("Set ${index + 1}: ${set.reps} × $weight kg")
            }
        }
    }
}
