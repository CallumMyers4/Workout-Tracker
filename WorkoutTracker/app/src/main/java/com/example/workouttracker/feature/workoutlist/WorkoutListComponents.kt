package com.example.workouttracker.feature.workoutlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import com.example.workouttracker.R
import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.formatDuration
import com.example.workouttracker.ui.theme.GenericCard
import java.time.format.DateTimeFormatter

// Create a selectable card containing a workout summary
@Composable
fun WorkoutCard(
    workout: WorkoutSummary,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    weightsUnit: WeightsUnit = WeightsUnit.METRIC,
) {
    GenericCard(
        title = workout.name,
        titleTrailing = {
            val isCardio = workout.type == WorkoutType.CARDIO
            Icon(
                painter = painterResource(if (isCardio) R.drawable.icon_bike else R.drawable.icon_weights),
                contentDescription = if (isCardio) "Cardio workout" else "Strength workout",
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = onOpen,
        modifier = modifier,
    ) {
        Text(
            workout.date?.format(DATE_FORMAT) ?: "Unknown date",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        if (workout.type == WorkoutType.CARDIO) {
            val distance = if (workout.totalDistanceMeters > 0) {
                " • ${weightsUnit.formatMeters(workout.totalDistanceMeters)} ${weightsUnit.distanceSymbol}"
            } else ""
            Text("${workout.totalDurationSeconds.formatDuration()}$distance", style = MaterialTheme.typography.bodySmall)
        }
        // Only display exercise names when the workout contains exercises
        if (workout.exerciseNames.isNotEmpty()) {
            Text(
                workout.exerciseNames.joinToString(", "),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic
            )
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
