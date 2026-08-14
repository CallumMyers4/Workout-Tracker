package com.example.workouttracker.feature.workouteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.WorkoutExerciseDraft

// Create an editable card for one exercise and all of its sets
@Composable
fun ExerciseEditorCard(
    exercise: WorkoutExerciseDraft,
    exerciseIndex: Int,
    catalog: List<CatalogExercise>,
    onSelected: (Long) -> Unit,
    onCreateExercise: (String) -> Unit,
    onOpenNote: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onAddSet: () -> Unit,
    onSetChanged: (Int, String, String) -> Unit,
    onRemoveSet: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep track of the exercise picker and removal confirmation dialogs
    var showPicker by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var confirmExerciseRemoval by remember { mutableStateOf(false) }
    var confirmSetRemoval by remember { mutableStateOf<Int?>(null) }

    Card(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Use the exercise name as the button which opens the exercise picker
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(exercise.name.ifBlank { "Select exercise" })
                }
                // Confirm removal when the exercise already contains entered information
                TextButton(
                    onClick = {
                        val hasContent = exercise.catalogExerciseId != null ||
                            exercise.sets.any { it.reps.isNotBlank() || it.weightKg.isNotBlank() }
                        if (hasContent) confirmExerciseRemoval = true else onRemove()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                    modifier = Modifier.size(40.dp).semantics {
                        contentDescription = "Remove exercise ${exerciseIndex + 1}"
                    },
                ) { Text("X") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription =
                            "${if (exercise.expanded) "Collapse" else "Expand"} exercise ${exerciseIndex + 1}"
                        stateDescription = if (exercise.expanded) "Expanded" else "Collapsed"
                    },
                ) { Text(if (exercise.expanded) "Hide" else "Show") }
                TextButton(onClick = onAddSet, modifier = Modifier.weight(1f)) { Text("Add set") }
                TextButton(
                    onClick = onOpenNote,
                    enabled = exercise.catalogExerciseId != null,
                    modifier = Modifier.weight(1f),
                ) { Text("Notes") }
            }
            if (exercise.expanded) {
                // Display an editable row for each set when the exercise is expanded
                exercise.sets.forEachIndexed { setIndex, set ->
                    SetEditorRow(
                        set = set,
                        setIndex = setIndex,
                        onChanged = { reps, weight -> onSetChanged(setIndex, reps, weight) },
                        onRemove = {
                            if (set.reps.isNotBlank() || set.weightKg.isNotBlank()) {
                                confirmSetRemoval = setIndex
                            } else {
                                onRemoveSet(setIndex)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                // Display a compact set summary when the exercise is hidden
                Text(
                    "${exercise.sets.size} set${if (exercise.sets.size == 1) "" else "s"}: " +
                        exercise.sets.joinToString(" • ") {
                            "${it.reps.ifBlank { "?" }} × ${it.weightKg.ifBlank { "?" }} kg"
                        },
                )
            }
        }
    }

    // Display a searchable list for selecting or creating an exercise
    if (showPicker) {
        val query = searchText.trim()
        // Filter the in-memory catalog using the entered search text
        val matchingExercises = catalog
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        AlertDialog(
            onDismissRequest = {
                showPicker = false
                searchText = ""
            },
            title = { Text("Select exercise") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search exercises") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isNotEmpty() && catalog.none { it.name.equals(query, ignoreCase = true) }) {
                        TextButton(
                            onClick = {
                                onCreateExercise(query)
                                showPicker = false
                                searchText = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add '$query' to the exercise library") }
                    }
                    when {
                        catalog.isEmpty() -> Text("No exercises are available. Add one in Settings first.")
                        matchingExercises.isEmpty() -> Text("No exercises match your search.")
                        else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                            items(matchingExercises, key = { it.id }) { option ->
                                TextButton(
                                    onClick = {
                                        onSelected(option.id)
                                        showPicker = false
                                        searchText = ""
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(option.name, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    showPicker = false
                    searchText = ""
                }) { Text("Cancel") }
            },
        )
    }
    // Ask for confirmation before removing an exercise which contains information
    if (confirmExerciseRemoval) {
        AlertDialog(
            onDismissRequest = { confirmExerciseRemoval = false },
            title = { Text("Remove exercise?") },
            text = { Text("The exercise and its entered sets will be removed from this draft.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExerciseRemoval = false
                    onRemove()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmExerciseRemoval = false }) { Text("Cancel") }
            },
        )
    }
    // Ask for confirmation before removing a set which contains information
    confirmSetRemoval?.let { setIndex ->
        AlertDialog(
            onDismissRequest = { confirmSetRemoval = null },
            title = { Text("Remove set ${setIndex + 1}?") },
            text = { Text("The repetitions and weight entered for this set will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSetRemoval = null
                    onRemoveSet(setIndex)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmSetRemoval = null }) { Text("Cancel") } },
        )
    }
}

// Create one row for entering the repetitions and weight of a set
@Composable
fun SetEditorRow(
    set: ExerciseSetDraft,
    setIndex: Int,
    onChanged: (reps: String, weightKg: String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    // Check the entered text without changing it while the user is typing
    val repsInvalid = set.reps.isNotEmpty() && (set.reps.toIntOrNull()?.let { it <= 0 } != false)
    val weight = set.weightKg.toDoubleOrNull()
    val weightInvalid = set.weightKg.isNotEmpty() && (weight == null || !weight.isFinite() || weight < 0.0)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            OutlinedTextField(
                value = set.reps,
                onValueChange = { onChanged(it, set.weightKg) },
                label = { Text("Set ${setIndex + 1} reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = dismissKeyboardActions(focusManager),
                isError = repsInvalid,
                supportingText = if (repsInvalid) ({ Text("Positive whole number") }) else null,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = set.weightKg,
                onValueChange = { onChanged(set.reps, it) },
                label = { Text("Weight kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = dismissKeyboardActions(focusManager),
                isError = weightInvalid,
                supportingText = if (weightInvalid) ({ Text("Zero or more") }) else null,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRemove,
                modifier = Modifier.size(40.dp).semantics {
                    contentDescription = "Remove set ${setIndex + 1}"
                },
            ) { Text("X") }
    }
}

// Remove focus and close the keyboard when the user presses Done
private fun dismissKeyboardActions(focusManager: FocusManager) = KeyboardActions(
    onDone = { focusManager.clearFocus() },
)
