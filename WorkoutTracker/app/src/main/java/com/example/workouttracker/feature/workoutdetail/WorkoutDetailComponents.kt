package com.example.workouttracker.feature.workoutdetail

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import com.example.workouttracker.core.model.WorkoutExercise
import com.example.workouttracker.ui.theme.GenericCard
import java.math.BigDecimal

// Create a card showing an exercise and all of its saved sets
@Composable
fun ExerciseSummaryCard(
    exercise: WorkoutExercise,
    modifier: Modifier = Modifier,
) {
    GenericCard(
        title = exercise.name,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        // Display sets using their original order in the workout
        exercise.sets.sortedBy { it.position }.forEachIndexed { index, set ->
            val weight = BigDecimal.valueOf(set.weightKg).stripTrailingZeros().toPlainString()
            Text("Set ${index + 1}: ${set.reps} × $weight kg")
        }
    }
}
