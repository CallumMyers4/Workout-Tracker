package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.workouttracker.core.model.CatalogExercise

// Display the exercise library and allow exercises to be added, renamed, or combined
@Composable
fun ExerciseLibraryDialog(
    exercises: List<CatalogExercise>,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onCombine: (sourceId: Long, targetId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep track of the exercise currently being added or renamed
    var input by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var combinePair by remember { mutableStateOf<Pair<CatalogExercise, CatalogExercise>?>(null) }
    var manageId by remember { mutableStateOf<Long?>(null) }
    var combineSource by remember { mutableStateOf<CatalogExercise?>(null) }
    var deleteTarget by remember { mutableStateOf<CatalogExercise?>(null) }
    val sorted = exercises.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    // Create the main exercise library dialog
    Dialog(onDismissRequest = onDismiss) {
        Surface(modifier) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exercise library")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; inputError = null },
                    label = { Text(if (editingId == null) "New exercise" else "Exercise name") },
                    isError = inputError != null,
                    supportingText = inputError?.let { message -> ({ Text(message) }) },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    // Validate the name before deciding whether to add, rename, or combine
                    val clean = input.trim()
                    if (clean.isEmpty()) {
                        inputError = "Enter a name."
                        return@TextButton
                    }
                    val source = editingId?.let { id -> exercises.firstOrNull { it.id == id } }
                    val target = exercises.firstOrNull {
                        it.id != editingId && it.name.equals(clean, ignoreCase = true)
                    }
                    when {
                        source != null && target != null -> combinePair = source to target
                        source != null -> onRename(source.id, clean)
                        target != null -> inputError = "That exercise already exists."
                        else -> onAdd(clean)
                    }
                    if (inputError == null && combinePair == null) {
                        input = ""
                        editingId = null
                    }
                }) { Text(if (editingId == null) "Add exercise" else "Save name") }

                if (sorted.isEmpty()) {
                    Text("No exercises yet. Add your first exercise above.")
                } else {
                    combineSource?.let { source ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Combine '${source.name}' with:")
                            TextButton(onClick = { combineSource = null }) { Text("Cancel") }
                        }
                    }
                    // Display each exercise in alphabetical order
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(sorted, key = { it.id }) { exercise ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(exercise.name)
                                }
                                val source = combineSource
                                if (source != null) {
                                    if (exercise.id == source.id) {
                                        Text("Selected")
                                    } else {
                                        TextButton(onClick = {
                                            combinePair = source to exercise
                                        }) { Text("Select") }
                                    }
                                } else {
                                    Box {
                                        TextButton(onClick = { manageId = exercise.id }) { Text("Manage") }
                                        DropdownMenu(
                                            expanded = manageId == exercise.id,
                                            onDismissRequest = { manageId = null },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    manageId = null
                                                    deleteTarget = exercise
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    manageId = null
                                                    editingId = exercise.id
                                                    input = exercise.name
                                                    inputError = null
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Combine") },
                                                enabled = exercises.size > 1,
                                                onClick = {
                                                    manageId = null
                                                    combineSource = exercise
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }

    deleteTarget?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete exercise?") },
            text = {
                Text(
                    "Delete '${exercise.name}' from the exercise library? " +
                        "Exercises used in saved workouts must be combined instead.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(exercise.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    // Ask for confirmation before moving history into an exercise which already exists
    combinePair?.let { (source, target) ->
        AlertDialog(
            onDismissRequest = { combinePair = null },
            title = { Text("Combine exercises?") },
            text = { Text("This will move all ${source.name} history into ${target.name} " +
                    "then delete ${source.name}.\n") },
            confirmButton = {
                TextButton(onClick = {
                    onCombine(source.id, target.id)
                    combinePair = null
                    combineSource = null
                    input = ""
                    editingId = null
                }) { Text("Combine") }
            },
            dismissButton = { TextButton(onClick = { combinePair = null }) { Text("Cancel") } },
        )
    }
}
