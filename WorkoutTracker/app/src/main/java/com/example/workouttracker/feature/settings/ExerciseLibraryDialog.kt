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
import com.example.workouttracker.core.model.ExerciseType
import com.example.workouttracker.ui.theme.GenericDropdown
import com.example.workouttracker.ui.theme.NotificationController
import com.example.workouttracker.ui.theme.NotificationPopupOverlay

// Display the exercise library and allow exercises to be added, renamed, or combined
@Composable
fun ExerciseLibraryDialog(
    exercises: List<CatalogExercise>,
    onAdd: (String, ExerciseType) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onCombine: (sourceId: Long, targetId: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    notificationController: NotificationController? = null,
) {
    // Keep track of the exercise currently being added or renamed
    var searchText by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<Long?>(null) }
    var showExerciseEditor by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var combinePair by remember { mutableStateOf<Pair<CatalogExercise, CatalogExercise>?>(null) }
    var manageId by remember { mutableStateOf<Long?>(null) }
    var combineSource by remember { mutableStateOf<CatalogExercise?>(null) }
    var deleteTarget by remember { mutableStateOf<CatalogExercise?>(null) }
    var newType by remember { mutableStateOf(ExerciseType.STRENGTH) }
    var filterType by remember { mutableStateOf(ExerciseType.STRENGTH) }
    val sorted = exercises.filter {
        it.type == filterType &&
            (searchText.isBlank() || it.name.contains(searchText.trim(), ignoreCase = true))
    }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    // Create the main exercise library dialog
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier) {
            Surface {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Exercise Library")
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search exercises") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        editingId = null
                        input = ""
                        inputError = null
                        newType = ExerciseType.STRENGTH
                        showExerciseEditor = true
                    }) { Text("Add exercise") }

                    GenericDropdown(
                        title = "Type",
                        values = ExerciseType.entries,
                        selected = filterType,
                        label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        onSelected = { filterType = it },
                        modifier = Modifier.fillMaxWidth(),
                    )

                if (sorted.isEmpty()) {
                    Text(if (exercises.isEmpty()) "No exercises yet." else "No exercises match your search and filter.")
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
                                Text(exercise.name)
                                val source = combineSource
                                if (source != null) {
                                    if (exercise.id == source.id) {
                                        Text("Selected")
                                    } else if (exercise.type != source.type) {
                                        Text("Different type")
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
                                                    showExerciseEditor = true
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
            // Mirror results above the separate dialog window while it is open
            notificationController?.let { controller ->
                NotificationPopupOverlay(
                    notification = controller.current,
                    onDismiss = controller::dismiss,
                )
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

    if (showExerciseEditor) {
        val source = editingId?.let { id -> exercises.firstOrNull { it.id == id } }
        AlertDialog(
            onDismissRequest = {
                showExerciseEditor = false
                inputError = null
            },
            title = { Text(if (source == null) "Add exercise" else "Rename exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; inputError = null },
                        label = { Text("Exercise name") },
                        singleLine = true,
                        isError = inputError != null,
                        supportingText = inputError?.let { message -> ({ Text(message) }) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (source == null) GenericDropdown(
                        title = "Exercise type",
                        values = ExerciseType.entries,
                        selected = newType,
                        label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                        onSelected = { newType = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = input.trim()
                    if (clean.isEmpty()) {
                        inputError = "Enter a name."
                        return@TextButton
                    }
                    val target = exercises.firstOrNull {
                        it.id != editingId && it.name.equals(clean, ignoreCase = true)
                    }
                    when {
                        source != null && target != null && source.type != target.type ->
                            inputError = "An exercise with that name exists with a different type."
                        source != null && target != null -> {
                            combinePair = source to target
                            showExerciseEditor = false
                        }
                        source != null -> {
                            onRename(source.id, clean)
                            showExerciseEditor = false
                        }
                        target != null -> inputError = "That exercise already exists."
                        else -> {
                            onAdd(clean, newType)
                            showExerciseEditor = false
                        }
                    }
                }) { Text(if (source == null) "Add" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExerciseEditor = false
                    inputError = null
                }) { Text("Cancel") }
            },
        )
    }
}
