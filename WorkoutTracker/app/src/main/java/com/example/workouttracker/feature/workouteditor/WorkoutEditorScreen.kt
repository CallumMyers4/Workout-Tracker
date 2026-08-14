package com.example.workouttracker.feature.workouteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.result.ValidationResult
import com.example.workouttracker.ui.theme.PageTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
// Function to display the new or edit workout page
@Composable
fun WorkoutEditorScreen(
    uiState: WorkoutEditorUiState,
    onNameChanged: (String) -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onAddExercise: () -> Unit,
    onRemoveExercise: (Int) -> Unit,
    onExerciseSelected: (Int, Long) -> Unit,
    onExerciseCreated: (Int, String) -> Unit,
    onOpenWorkoutNote: () -> Unit,
    onOpenExerciseNote: (Int) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSaveNote: () -> Unit,
    onClearNote: () -> Unit,
    onCloseNote: () -> Unit,
    onExerciseToggled: (Int) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (Int, Int) -> Unit,
    onSetChanged: (Int, Int, String, String) -> Unit,
    onRequestClear: () -> Unit,
    onCancelClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var showDatePicker by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val invalid = uiState.validationResult as? ValidationResult.Invalid
    // Scroll to an exercise when one of its inputs fails validation
    LaunchedEffect(invalid) {
        invalid?.exerciseIndex?.let { listState.animateScrollToItem(it) }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // Keep workout information fixed above the scrolling exercise list
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTitle(
                "Log",
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.draft.name,
                    onValueChange = onNameChanged,
                    label = { Text("Workout name") },
                    singleLine = true,
                    isError = invalid?.field == com.example.workouttracker.core.result.WorkoutField.NAME,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(uiState.draft.date.format(EDITOR_DATE_FORMAT))
                }
            }
            OutlinedButton(
                onClick = onOpenWorkoutNote,
                enabled = uiState.draft.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text("Workout note (shared by workout name)") }
            uiState.errorMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
            invalid?.let { Text(it.message, Modifier.padding(horizontal = 16.dp)) }
        }
        // Display each exercise inside the scrolling section of the page
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            uiState.draft.exercises.forEachIndexed { exerciseIndex, exercise ->
                item(key = exercise.editorKey) {
                    ExerciseEditorCard(
                        exercise = exercise,
                        exerciseIndex = exerciseIndex,
                        catalog = uiState.exerciseCatalog,
                        onSelected = { onExerciseSelected(exerciseIndex, it) },
                        onCreateExercise = { onExerciseCreated(exerciseIndex, it) },
                        onOpenNote = { onOpenExerciseNote(exerciseIndex) },
                        onToggle = { onExerciseToggled(exerciseIndex) },
                        onRemove = { onRemoveExercise(exerciseIndex) },
                        onAddSet = { onAddSet(exerciseIndex) },
                        onSetChanged = { setIndex, reps, weight ->
                            onSetChanged(exerciseIndex, setIndex, reps, weight)
                        },
                        onRemoveSet = { onRemoveSet(exerciseIndex, it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // Hide workout action buttons while typing to leave more room above the keyboard
        if (!keyboardVisible) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Clear button
                OutlinedButton(
                    onClick = onRequestClear,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("Clear", fontWeight = FontWeight.Bold) }
                // Add Exercise button
                OutlinedButton(onClick = onAddExercise, modifier = Modifier.weight(1.5f)) {
                    Text("Add Exercise", fontWeight = FontWeight.Bold)
                }
                // Save button
                Button(onClick = onSave, enabled = !uiState.isSaving, modifier = Modifier.weight(1f)) {
                    if (uiState.isSaving) CircularProgressIndicator() else
                        Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Display the date picker using a limited range around the current year
    if (showDatePicker) {
        val today = LocalDate.now()
        val firstDate = LocalDate.of(today.year - 20, 1, 1)
        val lastDate = LocalDate.of(today.year + 5, 12, 31)
        val initialMillis = uiState.draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            yearRange = firstDate.year..lastDate.year,
            selectableDates = remember(firstDate, lastDate) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val date = Instant.ofEpochMilli(utcTimeMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        return date in firstDate..lastDate
                    }

                    override fun isSelectableYear(year: Int): Boolean = year in firstDate.year..lastDate.year
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDateChanged(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Use date") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDateChanged(today)
                    showDatePicker = false
                }) { Text("Today") }
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(pickerState) }
    }
    // Ask for confirmation before clearing an unfinished workout
    if (uiState.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            title = { Text("Clear workout?") },
            text = { Text("All unsaved input in this editor will be removed.") },
            confirmButton = { TextButton(onClick = onConfirmClear) { Text("Clear") } },
            dismissButton = { TextButton(onClick = onCancelClear) { Text("Cancel") } },
        )
    }
    // Display the note editor when a workout or exercise note has been opened
    uiState.noteEditor?.let { editor ->
        NoteDialog(
            state = editor,
            onChanged = onNoteChanged,
            onSave = onSaveNote,
            onClear = onClearNote,
            onClose = onCloseNote,
        )
    }
}

// Format workout dates for display in the editor
private val EDITOR_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu")
