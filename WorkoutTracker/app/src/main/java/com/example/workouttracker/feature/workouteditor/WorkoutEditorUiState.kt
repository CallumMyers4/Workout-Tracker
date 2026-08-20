package com.example.workouttracker.feature.workouteditor

import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.result.ValidationResult
import java.io.Serializable

// Current state of the workout editor page
data class WorkoutEditorUiState(
    val draft: WorkoutDraft = WorkoutDraft(),
    val exerciseCatalog: List<CatalogExercise> = emptyList(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val validationResult: ValidationResult? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val noteEditor: NoteEditorState? = null,
)

// Current state of a dialog allowing the user to update a note
data class NoteEditorState(
    val scope: NoteScope,
    val targetId: Long? = null,
    val targetName: String,
    val input: String,
    val original: String,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
) : Serializable

// Type of item which owns the note being edited
enum class NoteScope : Serializable {
    WORKOUT_NAME,
    EXERCISE,
}

// One-time action sent by the workout editor page
sealed interface WorkoutEditorEvent {
    // Tell Home to return to the updated workout after an edit is saved
    data class Saved(val workoutId: Long) : WorkoutEditorEvent
}
