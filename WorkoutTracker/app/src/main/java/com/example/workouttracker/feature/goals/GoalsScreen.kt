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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.workouttracker.ui.theme.PageTitle

// Function to display the goals screen
@Composable
fun GoalsScreen(
    uiState: GoalsUiState,
    onEditGoal: (Long) -> Unit,
    onGoalInputChanged: (String) -> Unit,
    onSaveGoal: () -> Unit,
    onDismissGoalEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PageTitle(
            text = "Progress",
        )
        when {
            // Loading UI
            uiState.isLoading && uiState.goals.isEmpty() -> CircularProgressIndicator(Modifier.padding(all = 24.dp))
            uiState.errorMessage != null && uiState.goals.isEmpty() -> Text(uiState.errorMessage, Modifier.padding(24.dp))
            uiState.goals.isEmpty() -> Text("Add exercises in Settings to create goals.", Modifier.padding(24.dp))
            // Display once loading is complete
            else -> LazyColumn(Modifier.fillMaxSize()) {
                // Loop over each goal and display a card for it
                items(uiState.goals, key = { it.exercise.id }) { progress ->
                    GoalCard(
                        progress = progress,
                        onUpdateGoal = { onEditGoal(progress.exercise.id) },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    )
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
                OutlinedTextField(
                    value = editor.input,
                    onValueChange = onGoalInputChanged,
                    label = { Text("Goal weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = editor.errorMessage != null,
                    supportingText = editor.errorMessage?.let { message -> ({ Text(message) }) },
                )
            },
            confirmButton = { TextButton(onClick = onSaveGoal) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismissGoalEditor) { Text("Cancel") } },
        )
    }
}
