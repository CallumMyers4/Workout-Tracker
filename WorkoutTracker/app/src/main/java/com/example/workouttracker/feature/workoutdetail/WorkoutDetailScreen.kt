package com.example.workouttracker.feature.workoutdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.formatDuration
import com.example.workouttracker.ui.theme.EmptyStateTextStyle
import com.example.workouttracker.ui.theme.PageTitle
import java.time.format.DateTimeFormatter

// Function to display the details of a saved workout
@Composable
fun WorkoutDetailScreen(
    uiState: WorkoutDetailUiState,
    weightsUnit: WeightsUnit,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the originating destination in sync with both system and title-bar back actions
    BackHandler(onBack = onBack)

    Column(modifier.fillMaxSize()) {
        // Title as the name if available, else none
        uiState.workout?.let {
            PageTitle(
                text = it.name,
                icon = painterResource(R.drawable.icon_back),
                onIconClick = onBack,
                iconContentDescription = "Back",
            )
        }
        when {
            // Display a loading indicator while the workout is being retrieved
            uiState.isLoading -> Column(Modifier
                .fillMaxSize()
                .padding(24.dp)) { CircularProgressIndicator() }
            // Display an error and allow the user to return when no workout can be loaded
            uiState.workout == null -> Column(Modifier
                .fillMaxSize()
                .padding(24.dp)) {
                Text(uiState.errorMessage ?: "Workout not found.")
                TextButton(onClick = onBack) { Text("Back") }
            }
            else -> {
                val workout = uiState.workout
                // Display the workout information followed by each exercise card
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(workout.name)
                            Text(workout.date.format(DATE_FORMAT))
                            Text(if (workout.type == WorkoutType.CARDIO) "Cardio" else "Strength")
                            Text("${workout.exercises.size} ${if (workout.exercises.size == 1) "exercise" else "exercises"}")
                            if (workout.type == WorkoutType.CARDIO) {
                                val duration = workout.exercises.sumOf { it.cardioEntry?.durationSeconds ?: 0L }
                                val distance = workout.exercises.sumOf { it.cardioEntry?.distanceMeters ?: 0.0 }
                                Text("Total duration: ${duration.formatDuration()}")
                                if (distance > 0) Text("Total distance: ${weightsUnit.formatMeters(distance)} ${weightsUnit.distanceSymbol}")
                            }
                            Button(onClick = { onEdit(workout.id) }) { Text("Edit workout") }
                            OutlinedButton(onClick = onRequestDelete, enabled = !uiState.isDeleting) {
                                Text(if (uiState.isDeleting) "Deleting…" else "Delete workout")
                            }
                        }
                    }
                    if (workout.exercises.isEmpty()) {
                        item {
                            Text(
                                text = "This workout has no exercises.",
                                modifier = Modifier.padding(24.dp),
                                style = EmptyStateTextStyle,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                    items(workout.exercises.sortedBy { it.position }, key = { it.id }) { exercise ->
                        ExerciseSummaryCard(exercise, weightsUnit, Modifier
                            .fillMaxWidth()
                            .padding(12.dp))
                    }
                }
            }
        }
    }
    // Ask for confirmation before permanently deleting the workout
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text("Delete workout?") },
            text = { Text("This permanently removes the workout and all of its entries.") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text("Cancel") } },
        )
    }
}

// Format workout dates for display on the details page
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM uuuu")
