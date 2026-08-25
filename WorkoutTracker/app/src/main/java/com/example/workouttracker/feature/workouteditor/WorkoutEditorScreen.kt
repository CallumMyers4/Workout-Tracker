package com.example.workouttracker.feature.workouteditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.core.result.ValidationResult
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.ui.theme.ActionButton
import com.example.workouttracker.ui.theme.DestructiveButton
import com.example.workouttracker.ui.theme.GenericButton
import com.example.workouttracker.ui.theme.PageTitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
// Function to display the new or edit workout page
@Composable
fun WorkoutEditorScreen(
    uiState: WorkoutEditorUiState,
    weightsUnit: WeightsUnit,
    isEditing: Boolean,
    onBack: () -> Unit,
    tabExitRequested: Boolean = false,
    onCancelTabExit: () -> Unit = {},
    onConfirmTabExit: () -> Unit = {},
    onNameChanged: (String) -> Unit,
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
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val nameBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    var scrollToAddedExercise by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val invalid = uiState.validationResult as? ValidationResult.Invalid
    val requestBack = {
        if (uiState.isDirty) showDiscardConfirmation = true else onBack()
    }
    BackHandler(enabled = isEditing, onBack = requestBack)
    LaunchedEffect(tabExitRequested) {
        if (tabExitRequested) {
            if (uiState.isDirty) showDiscardConfirmation = true else onConfirmTabExit()
        }
    }
    // Scroll to an exercise when one of its inputs fails validation
    LaunchedEffect(invalid) {
        invalid?.exerciseIndex?.let { listState.animateScrollToItem(it) }
    }
    // Keep exercises added at the bottom of the draft visible.
    LaunchedEffect(uiState.draft.exercises.size) {
        val exerciseCount = uiState.draft.exercises.size
        if (scrollToAddedExercise && exerciseCount > 0) {
            listState.animateScrollToItem(exerciseCount - 1)
            scrollToAddedExercise = false
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        // Keep workout information fixed above the scrolling exercise list
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PageTitle(
                text = if (isEditing) "Edit workout" else "Log",
                icon = painterResource(
                    if (isEditing) R.drawable.icon_back else R.drawable.icon_add
                ),
                onIconClick = if (isEditing) requestBack else null,
                iconContentDescription = if (isEditing) "Back to workout details" else null,
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = bringIntoViewOnDone(
                        focusManager = focusManager,
                        requester = nameBringIntoViewRequester,
                        coroutineScope = coroutineScope,
                        density = density,
                        imeInsets = imeInsets,
                    ),
                    isError = invalid?.field == com.example.workouttracker.core.result.WorkoutField.NAME,
                    modifier = Modifier
                        .weight(1.5f)
                        .bringIntoViewRequester(nameBringIntoViewRequester),
                )
                }

            GenericButton(
                text = "Notes",
                onClick = onOpenWorkoutNote,
                enabled = uiState.draft.name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
            uiState.errorMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
            uiState.statusMessage?.let { Text(it, Modifier.padding(horizontal = 16.dp)) }
            invalid?.let { Text(it.message, Modifier.padding(horizontal = 16.dp)) }
        }
        // Display each exercise inside the scrolling section of the page
        LazyColumn(modifier = Modifier.weight(1f), state = listState) {
            uiState.draft.exercises.forEachIndexed { exerciseIndex, exercise ->
                item(key = exercise.editorKey) {
                    ExerciseEditorCard(
                        exercise = exercise,
                        weightsUnit = weightsUnit,
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
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
                    .padding(12.dp),
            ) {
                val useCompactLabels = maxWidth < 360.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Clear a new draft or restore an edit to its originally loaded values
                    DestructiveButton(
                        text = if (isEditing) "Reset" else "Clear",
                        onClick = onRequestClear,
                        enabled = !uiState.isSaving && (!isEditing || uiState.isDirty),
                        onCard = useCompactLabels,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )

                    // Create add exercise button
                    GenericButton(
                        text = "Add Exercise",
                        onClick = {
                            scrollToAddedExercise = true
                            onAddExercise()
                        },
                        onCard = useCompactLabels,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )

                    // Create save button
                    ActionButton(
                        text = "Save",
                        onClick = onSave,
                        enabled = !uiState.isSaving,
                        onCard = useCompactLabels,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }

    // Ask for confirmation before clearing or resetting a workout
    if (uiState.showClearConfirmation) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            title = { Text(if (isEditing) "Reset workout?" else "Clear workout?") },
            text = {
                Text(
                    if (isEditing) "Restore this workout to how it was when editing began."
                    else "All unsaved input in this editor will be removed."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmClear) {
                    Text(if (isEditing) "Reset" else "Clear")
                }
            },
            dismissButton = { TextButton(onClick = onCancelClear) { Text("Cancel") } },
        )
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDiscardConfirmation = false
                if (tabExitRequested) onCancelTabExit()
            },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes to this workout will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    if (tabExitRequested) onConfirmTabExit() else onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    if (tabExitRequested) onCancelTabExit()
                }) {
                    Text("Keep editing")
                }
            },
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

internal fun bringIntoViewOnDone(
    focusManager: FocusManager,
    requester: BringIntoViewRequester,
    coroutineScope: CoroutineScope,
    density: Density,
    imeInsets: WindowInsets,
) = KeyboardActions(onDone = {
    coroutineScope.launch {
        requester.bringIntoView()
        focusManager.clearFocus()
        // The editor actions and app navigation return after the IME closes,
        // reducing the viewport. Scroll again after that new layout is applied.
        snapshotFlow { imeInsets.getBottom(density) }.first { it == 0 }
        withFrameNanos { }
        withFrameNanos { }
        requester.bringIntoView()
    }
})
