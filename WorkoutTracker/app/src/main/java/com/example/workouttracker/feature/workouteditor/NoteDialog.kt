package com.example.workouttracker.feature.workouteditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Create a dialog for editing a shared workout or exercise note
@Composable
fun NoteDialog(
    state: NoteEditorState,
    onChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    // Keep track of whether the user is confirming that a stored note should be removed
    var confirmClear by remember { mutableStateOf(false) }
    val scopeDescription = when (state.scope) {
        NoteScope.WORKOUT_NAME -> "Shared by every workout named '${state.targetName}', ignoring case."
        NoteScope.EXERCISE -> "Shared by every '${state.targetName}' entry in the exercise library."
    }
    // Display the note editor and explain which workouts or exercises share its value
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Note for ${state.targetName}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(scopeDescription)
                OutlinedTextField(
                    value = state.input,
                    onValueChange = onChanged,
                    label = { Text("Multiline note") },
                    minLines = 5,
                    maxLines = 12,
                    isError = state.errorMessage != null,
                    supportingText = state.errorMessage?.let { message -> ({ Text(message) }) },
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                )
                if (state.original.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }, enabled = !state.isSaving) {
                        Text("Clear stored note")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(if (state.isSaving) "Saving…" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close without saving") } },
    )
    // Ask for confirmation before permanently clearing a stored note
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear note?") },
            text = { Text("This removes the shared note. This action cannot be undone.") },
            confirmButton = { TextButton(onClick = onClear) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
