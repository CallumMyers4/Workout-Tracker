package com.example.workouttracker.feature.workoutlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.WorkoutSummary
import java.time.format.DateTimeFormatter

// Create a selectable card containing a workout summary
@Composable
fun WorkoutCard(
    workout: WorkoutSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onOpen, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(workout.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(workout.date?.format(DATE_FORMAT) ?: "Unknown date")
            Text("${workout.exerciseCount} distinct exercise${if (workout.exerciseCount == 1) "" else "s"}")
            // Only display exercise names when the workout contains exercises
            if (workout.exerciseNames.isNotEmpty()) {
                Text(
                    workout.exerciseNames.joinToString(", "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Create a heading which can expand or collapse a group of workouts
@Composable
fun WorkoutGroupHeader(
    title: String,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics {
                heading()
                stateDescription = if (collapsed) "Collapsed" else "Expanded"
            },
    ) {
        TextButton(onClick = onToggle) {
            Text(if (collapsed) "Expand" else "Collapse")
            Spacer(Modifier.width(8.dp))
            Text(title)
        }
    }
}

// Format workout dates for display in summary cards
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu")
