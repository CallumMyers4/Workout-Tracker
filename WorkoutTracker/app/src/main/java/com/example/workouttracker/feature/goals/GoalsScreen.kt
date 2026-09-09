package com.example.workouttracker.feature.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.isValidWeightInput
import com.example.workouttracker.ui.theme.EmptyStateTextStyle
import com.example.workouttracker.ui.theme.PageTitle

// Function to display the goals screen
@Composable
fun GoalsScreen(
    uiState: GoalsUiState,
    weightsUnit: WeightsUnit,
    onEditGoal: (Long) -> Unit,
    onGoalInputChanged: (String) -> Unit,
    onGoalMinutesChanged: (String) -> Unit,
    onGoalSecondsChanged: (String) -> Unit,
    onTypeSelected: (WorkoutType) -> Unit,
    onSaveGoal: () -> Unit,
    onDismissGoalEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTitle(
            text = "Progress",
            icon = painterResource(R.drawable.icon_progress),
        )
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            listOf(WorkoutType.STRENGTH, WorkoutType.CARDIO).forEach { type ->
                TextButton(onClick = { onTypeSelected(type) }, modifier = Modifier.weight(1f)) {
                    Text(type.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = if (uiState.selectedType == type) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        when {
            // Loading UI
            uiState.isLoading && uiState.goals.isEmpty() -> CircularProgressIndicator(Modifier.padding(all = 24.dp))
            uiState.errorMessage != null && uiState.goals.isEmpty() -> Text(uiState.errorMessage, Modifier.padding(24.dp))
            uiState.selectedType == WorkoutType.STRENGTH && uiState.goals.isEmpty() ||
                uiState.selectedType == WorkoutType.CARDIO && uiState.cardioGoals.isEmpty() -> Text(
                text = "Add exercises in Settings to create goals.",
                modifier = Modifier.padding(24.dp),
                style = EmptyStateTextStyle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Display once loading is complete
            else -> LazyColumn(Modifier.fillMaxSize()) {
                // Loop over each goal and display a card for it
                if (uiState.selectedType == WorkoutType.STRENGTH) items(uiState.goals, key = { it.exercise.id }) { progress ->
                    GoalCard(
                        progress = progress,
                        weightsUnit = weightsUnit,
                        onUpdateGoal = { onEditGoal(progress.exercise.id) },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
                }
                else items(uiState.cardioGoals, key = { it.exercise.id }) { progress ->
                    CardioGoalCard(progress, weightsUnit, { onEditGoal(progress.exercise.id) }, Modifier.fillMaxWidth().padding(12.dp))
                }
            }
        }
    }

    // Create a dialog for updating goals
    uiState.editor?.let { editor ->
        AlertDialog(
            onDismissRequest = onDismissGoalEditor,
            title = { Text("Goal for ${editor.exerciseName}") },
            text = {
                Column {
                if (editor.type == WorkoutType.CARDIO) {
                    editor.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    editor.distanceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                OutlinedTextField(
                    value = editor.input,
                    onValueChange = { value ->
                        if (value.isValidWeightInput()) onGoalInputChanged(value)
                    },
                    label = { Text(if (editor.type == WorkoutType.STRENGTH) "Goal weight (${weightsUnit.symbol})" else "Goal distance (${weightsUnit.distanceSymbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = editor.errorMessage != null || editor.distanceError != null,
                    supportingText = if (editor.type == WorkoutType.STRENGTH) {
                        editor.errorMessage?.let { message -> ({ Text(message) }) }
                    } else null,
                )
                if (editor.type == WorkoutType.CARDIO) {
                    editor.timeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    androidx.compose.foundation.layout.Row {
                    OutlinedTextField(
                        value = editor.durationMinutes,
                        onValueChange = onGoalMinutesChanged,
                        label = { Text("mm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = editor.timeError != null,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = editor.durationSeconds,
                        onValueChange = onGoalSecondsChanged,
                        label = { Text("ss") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = editor.timeError != null,
                        modifier = Modifier.weight(1f),
                    )
                    }
                }
                }
            },
            confirmButton = { TextButton(onClick = onSaveGoal) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismissGoalEditor) { Text("Cancel") } },
        )
    }
}
