package com.example.workouttracker.feature.workouteditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.Workout
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.AppNotification
import com.example.workouttracker.core.model.AppNotificationType
import com.example.workouttracker.core.model.WorkoutExerciseDraft
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.ExerciseType
import com.example.workouttracker.core.model.CardioEntryDraft
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.result.ValidationResult
import com.example.workouttracker.domain.repository.ExerciseRepository
import com.example.workouttracker.domain.repository.WorkoutRepository
import com.example.workouttracker.domain.service.WorkoutValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.math.BigDecimal

// Manage the editable workout draft and all actions from the workout editor page
class WorkoutEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val validator: WorkoutValidator,
) : ViewModel() {
    // Store page state separately from one-time actions such as opening the saved workout
    private val _uiState = MutableStateFlow(WorkoutEditorUiState())
    val uiState: StateFlow<WorkoutEditorUiState> = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<WorkoutEditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<WorkoutEditorEvent> = _events.asSharedFlow()
    private var originalDraft = savedStateHandle.get<WorkoutDraft>(ORIGINAL_DRAFT_KEY)
    private var fullCatalog = emptyList<com.example.workouttracker.core.model.CatalogExercise>()
    // Keep the exact persisted values separate from converted editor text
    private var originalKilogramDraft = savedStateHandle.get<WorkoutDraft>(ORIGINAL_KILOGRAM_DRAFT_KEY)
    // Keep cardio distances in meters so rounded display text is never used as the next conversion source
    private var canonicalCardioDraft = savedStateHandle.get<WorkoutDraft>(CANONICAL_CARDIO_DRAFT_KEY)
    // Restore the unit used by saved draft text so it can be converted safely
    private var weightsUnit = savedStateHandle.get<String>(WEIGHTS_UNIT_KEY)
        ?.let { runCatching { WeightsUnit.valueOf(it) }.getOrNull() }
        ?: WeightsUnit.METRIC

    // Restore an unfinished draft or load the workout selected for editing
    init {
        val restoredDraft = savedStateHandle.get<WorkoutDraft>(DRAFT_KEY)
        val restoredNote = savedStateHandle.get<NoteEditorState>(NOTE_EDITOR_KEY)
        val workoutId = savedStateHandle.get<Long>("workoutId")
        if (restoredNote != null) _uiState.update { it.copy(noteEditor = restoredNote) }
        if (restoredDraft != null) {
            if (canonicalCardioDraft == null) {
                canonicalCardioDraft = restoredDraft.withCanonicalDistances(weightsUnit)
                savedStateHandle[CANONICAL_CARDIO_DRAFT_KEY] = canonicalCardioDraft
            }
            _uiState.update { it.copy(draft = restoredDraft, isDirty = true, selectedType = restoredDraft.type) }
        }
        if (workoutId != null &&
            (restoredDraft == null || originalDraft == null || originalKilogramDraft == null)
        ) {
            viewModelScope.launch {
                runCatching { workoutRepository.observeWorkout(workoutId).filterNotNull().first() }
                    .onSuccess { workout ->
                        val kilogramDraft = workout.toKilogramDraft()
                        val metricDraft = kilogramDraft.withCanonicalDistancesDisplayed()
                        val loadedDraft = metricDraft.convertWeights(WeightsUnit.METRIC, weightsUnit)
                        originalKilogramDraft = kilogramDraft
                        savedStateHandle[ORIGINAL_KILOGRAM_DRAFT_KEY] = kilogramDraft
                        if (restoredDraft == null) {
                            canonicalCardioDraft = kilogramDraft
                            savedStateHandle[CANONICAL_CARDIO_DRAFT_KEY] = kilogramDraft
                        }
                        if (originalDraft == null) {
                            originalDraft = loadedDraft
                            savedStateHandle[ORIGINAL_DRAFT_KEY] = loadedDraft
                        }
                        if (restoredDraft == null) {
                            _uiState.update { it.copy(
                                draft = loadedDraft, isDirty = false, selectedType = loadedDraft.type,
                                exerciseCatalog = fullCatalog.filter { exercise -> exercise.type.name == loadedDraft.type.name },
                            ) }
                        }
                    }
                    .onFailure { error ->
                        _uiState.update { it.copy(errorMessage = error.userMessage()) }
                    }
            }
        }
        viewModelScope.launch {
            // Keep selected exercise names updated when the exercise library changes
            exerciseRepository.observeCatalog()
                .catch { error -> _uiState.update { it.copy(errorMessage = error.userMessage()) } }
                .collect { catalog ->
                    fullCatalog = catalog
                    val namesById = catalog.associate { it.id to it.name }
                    _uiState.update { state ->
                        state.copy(
                            exerciseCatalog = catalog.filter { it.type.name == state.draft.type.name },
                            draft = state.draft.copy(
                                exercises = state.draft.exercises.map { row ->
                                    row.copy(name = row.catalogExerciseId?.let(namesById::get) ?: row.name)
                                },
                            ),
                        )
                    }
                }
        }
    }

    // Convert the editable text when the display unit changes; persisted values remain kilograms
    fun setWeightsUnit(unit: WeightsUnit) {
        if (unit == weightsUnit) return
        val previous = weightsUnit
        weightsUnit = unit
        savedStateHandle[WEIGHTS_UNIT_KEY] = unit.name
        originalDraft = originalDraft?.convertWeights(previous, unit, originalKilogramDraft)
        originalDraft?.let { savedStateHandle[ORIGINAL_DRAFT_KEY] = it }
        _uiState.update { state ->
            val converted = state.draft.convertWeights(previous, unit, canonicalCardioDraft)
            if (state.isDirty) savedStateHandle[DRAFT_KEY] = converted
            state.copy(draft = converted)
        }
    }

    // Update the name of the workout draft
    fun updateWorkoutName(name: String) {
        changeDraft { it.copy(name = name) }
    }

    fun selectWorkoutType(type: WorkoutType) {
        val draft = WorkoutDraft(type = type)
        canonicalCardioDraft = draft.withCanonicalDistances(weightsUnit)
        savedStateHandle[DRAFT_KEY] = draft
        savedStateHandle[CANONICAL_CARDIO_DRAFT_KEY] = canonicalCardioDraft
        _uiState.update { it.copy(
            draft = draft, selectedType = type, isDirty = false,
            exerciseCatalog = fullCatalog.filter { exercise -> exercise.type.name == type.name },
        ) }
    }

    fun requestBackToChooser() {
        val state = _uiState.value
        if (state.isDirty && state.draft.hasMeaningfulContent()) {
            _uiState.update { it.copy(showClearConfirmation = true, returnToChooserAfterClear = true) }
        } else returnToChooser()
    }

    private fun returnToChooser() {
        savedStateHandle.remove<WorkoutDraft>(DRAFT_KEY)
        savedStateHandle.remove<WorkoutDraft>(CANONICAL_CARDIO_DRAFT_KEY)
        canonicalCardioDraft = null
        _uiState.update { it.copy(
            draft = WorkoutDraft(), selectedType = null, isDirty = false,
            showClearConfirmation = false, returnToChooserAfterClear = false,
            validationResult = null, errorMessage = null,
        ) }
    }

    // Update the date of the workout draft
    fun updateWorkoutDate(date: LocalDate) {
        changeDraft { it.copy(date = date) }
    }

    // Add a new exercise and hide the exercises which are already present
    fun addExercise() {
        changeDraft { draft ->
            draft.copy(
                exercises = draft.exercises.map { it.copy(expanded = false) } + WorkoutExerciseDraft(),
            )
        }
    }

    // Remove an exercise and keep one blank row when the list becomes empty
    fun removeExercise(exerciseIndex: Int) {
        changeDraft { draft ->
            if (exerciseIndex !in draft.exercises.indices) return@changeDraft draft
            val remaining = draft.exercises.filterIndexed { index, _ -> index != exerciseIndex }
            draft.copy(exercises = remaining.ifEmpty { listOf(WorkoutExerciseDraft()) })
        }
    }

    // Select an exercise from the library for one editor row
    fun selectExercise(exerciseIndex: Int, catalogExerciseId: Long) {
        val selected = _uiState.value.exerciseCatalog.firstOrNull { it.id == catalogExerciseId } ?: return
        changeDraft { draft ->
            draft.updateExercise(exerciseIndex) {
                it.copy(catalogExerciseId = selected.id, name = selected.name)
            }
        }
    }

    // Create a new library exercise and select it for one editor row
    fun createAndSelectExercise(exerciseIndex: Int, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch {
            runCatching { exerciseRepository.addExercise(cleanName, ExerciseType.valueOf(_uiState.value.draft.type.name)) }
                .onSuccess { id ->
                    changeDraft { draft ->
                        draft.updateExercise(exerciseIndex) {
                            it.copy(catalogExerciseId = id, name = cleanName)
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(errorMessage = error.userMessage()) }
                }
        }
    }

    // Load the note shared by workouts with the current workout name
    fun openWorkoutNote() {
        val workoutName = _uiState.value.draft.name.trim()
        if (workoutName.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter a workout name before opening its shared note.") }
            return
        }
        viewModelScope.launch {
            runCatching { exerciseRepository.observeWorkoutNameNote(workoutName).first().orEmpty() }
                .onSuccess { note ->
                    setNoteEditor(
                        NoteEditorState(
                            scope = NoteScope.WORKOUT_NAME,
                            targetName = workoutName,
                            input = note,
                            original = note,
                        ),
                    )
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.userMessage()) } }
        }
    }

    // Open the shared note for the selected exercise
    fun openExerciseNote(exerciseIndex: Int) {
        val row = _uiState.value.draft.exercises.getOrNull(exerciseIndex) ?: return
        val id = row.catalogExerciseId
        if (id == null) {
            _uiState.update { it.copy(errorMessage = "Select an exercise before opening its shared note.") }
            return
        }
        val catalogExercise = _uiState.value.exerciseCatalog.firstOrNull { it.id == id } ?: return
        val note = catalogExercise.note.orEmpty()
        setNoteEditor(
            NoteEditorState(
                scope = NoteScope.EXERCISE,
                targetId = id,
                targetName = catalogExercise.name,
                input = note,
                original = note,
            ),
        )
    }

    // Update the text inside the open note editor
    fun updateNote(value: String) {
        val editor = _uiState.value.noteEditor ?: return
        setNoteEditor(editor.copy(input = value, errorMessage = null))
    }

    // Close the note editor and remove its saved state
    fun closeNote() {
        savedStateHandle.remove<NoteEditorState>(NOTE_EDITOR_KEY)
        _uiState.update { it.copy(noteEditor = null) }
    }

    // Save the text currently entered in the note editor
    fun saveNote() = persistNote(clear = false)

    // Remove the stored note
    fun clearNote() = persistNote(clear = true)

    // Save or clear a workout or exercise note using the correct repository operation
    private fun persistNote(clear: Boolean) {
        val editor = _uiState.value.noteEditor ?: return
        if (editor.isSaving) return
        setNoteEditor(editor.copy(isSaving = true, errorMessage = null))
        viewModelScope.launch {
            val note = if (clear) null else editor.input
            runCatching {
                when (editor.scope) {
                    NoteScope.WORKOUT_NAME ->
                        exerciseRepository.setWorkoutNameNote(editor.targetName, note)
                    NoteScope.EXERCISE ->
                        exerciseRepository.setExerciseNote(requireNotNull(editor.targetId), note)
                }
            }.onSuccess { closeNote() }
                .onFailure { error ->
                    setNoteEditor(editor.copy(isSaving = false, errorMessage = error.userMessage()))
                }
        }
    }

    // Update and preserve the open note editor state
    private fun setNoteEditor(editor: NoteEditorState) {
        savedStateHandle[NOTE_EDITOR_KEY] = editor
        _uiState.update { it.copy(noteEditor = editor) }
    }

    // Expand or hide one exercise editor card
    fun toggleExerciseExpanded(exerciseIndex: Int) {
        changeDraft { draft ->
            draft.updateExercise(exerciseIndex) { it.copy(expanded = !it.expanded) }
        }
    }

    // Add a blank set and expand its exercise
    fun addSet(exerciseIndex: Int) {
        changeDraft { draft ->
            draft.updateExercise(exerciseIndex) {
                it.copy(expanded = true, sets = it.sets + ExerciseSetDraft())
            }
        }
    }

    // Remove a set and keep one blank row when the exercise has no sets left
    fun removeSet(exerciseIndex: Int, setIndex: Int) {
        changeDraft { draft ->
            draft.updateExercise(exerciseIndex) { exercise ->
                if (setIndex !in exercise.sets.indices) return@updateExercise exercise
                val remaining = exercise.sets.filterIndexed { index, _ -> index != setIndex }
                exercise.copy(sets = remaining.ifEmpty { listOf(ExerciseSetDraft()) })
            }
        }
    }

    // Update the repetitions and weight text for one set
    fun updateSet(exerciseIndex: Int, setIndex: Int, reps: String, weightKg: String) {
        changeDraft { draft ->
            draft.updateExercise(exerciseIndex) { exercise ->
                if (setIndex !in exercise.sets.indices) return@updateExercise exercise
                exercise.copy(
                    sets = exercise.sets.mapIndexed { index, set ->
                        if (index == setIndex) set.copy(reps = reps, weightKg = weightKg) else set
                    },
                )
            }
        }
    }

    fun updateCardioEntry(exerciseIndex: Int, minutes: String, seconds: String, distance: String) {
        changeDraft { draft ->
            draft.updateExercise(exerciseIndex) { exercise ->
                exercise.copy(cardioEntry = CardioEntryDraft(minutes, seconds, distance))
            }
        }
    }

    // Open the clear confirmation when the draft contains unsaved information
    fun requestClear() {
        val state = _uiState.value
        val isEditing = savedStateHandle.get<Long>("workoutId") != null
        if (state.isDirty && (isEditing || state.draft.hasMeaningfulContent())) {
            _uiState.update { it.copy(showClearConfirmation = true) }
        }
    }

    // Close the clear workout confirmation
    fun cancelClear() {
        _uiState.update { it.copy(showClearConfirmation = false, returnToChooserAfterClear = false) }
    }

    // Reset the editor to a new blank workout
    fun confirmClear() {
        val editedWorkoutId = savedStateHandle.get<Long>("workoutId")
        val shouldReturn = _uiState.value.returnToChooserAfterClear && editedWorkoutId == null
        if (shouldReturn) {
            returnToChooser()
            return
        }
        val clearedDraft = if (editedWorkoutId == null) {
            WorkoutDraft(date = LocalDate.now(), type = _uiState.value.draft.type)
        } else {
            originalDraft ?: return
        }
        savedStateHandle.remove<WorkoutDraft>(DRAFT_KEY)
        canonicalCardioDraft = if (editedWorkoutId == null) {
            clearedDraft.withCanonicalDistances(weightsUnit)
        } else originalKilogramDraft
        canonicalCardioDraft?.let { savedStateHandle[CANONICAL_CARDIO_DRAFT_KEY] = it }
        _uiState.update {
            it.copy(
                draft = clearedDraft,
                isDirty = false,
                showClearConfirmation = false,
                validationResult = null,
                errorMessage = null,
                returnToChooserAfterClear = false,
            )
        }
    }

    // Validate and save; edits return to Home details while Log resets to a fresh draft
    fun save() {
        if (_uiState.value.isSaving) return
        val draft = _uiState.value.draft
        val validation = validator.validate(draft)
        if (validation is ValidationResult.Invalid) {
            _uiState.update { it.copy(validationResult = validation) }
            return
        }
        _uiState.update { it.copy(isSaving = true, validationResult = ValidationResult.Valid, errorMessage = null) }
        viewModelScope.launch {
            // Always pass kilogram values to the repository and Room database
            runCatching {
                workoutRepository.saveWorkout(
                    draft.toKilogramsPreservingUnchanged(
                        weightsUnit,
                        originalDraft,
                        originalKilogramDraft,
                        canonicalCardioDraft,
                    ),
                )
            }
                .onSuccess { workoutId ->
                    savedStateHandle.remove<WorkoutDraft>(DRAFT_KEY)
                    if (draft.workoutId == null) {
                        savedStateHandle.remove<WorkoutDraft>(CANONICAL_CARDIO_DRAFT_KEY)
                        canonicalCardioDraft = null
                        _uiState.update {
                            it.copy(
                                draft = WorkoutDraft(date = LocalDate.now()),
                                selectedType = null,
                                isSaving = false,
                                isDirty = false,
                                validationResult = null,
                            )
                        }
                        _events.emit(
                            WorkoutEditorEvent.Notify(
                                AppNotification("Workout saved.", AppNotificationType.SUCCESS),
                            ),
                        )
                    } else {
                        _uiState.update { it.copy(isSaving = false, isDirty = false) }
                        _events.emit(
                            WorkoutEditorEvent.Notify(
                                AppNotification("Workout updated.", AppNotificationType.SUCCESS),
                            ),
                        )
                        _events.emit(WorkoutEditorEvent.Saved(workoutId))
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = error.userMessage()) }
                    _events.emit(
                        WorkoutEditorEvent.Notify(
                            AppNotification(error.userMessage(), AppNotificationType.ERROR),
                        ),
                    )
                }
        }
    }

    // Apply a draft change and clear errors which may no longer be valid
    private fun changeDraft(transform: (WorkoutDraft) -> WorkoutDraft) {
        _uiState.update { state ->
            val changed = transform(state.draft)
            canonicalCardioDraft = changed.withUpdatedCanonicalDistances(
                previousDisplay = state.draft,
                previousCanonical = canonicalCardioDraft,
                from = weightsUnit,
            )
            savedStateHandle[DRAFT_KEY] = changed
            savedStateHandle[CANONICAL_CARDIO_DRAFT_KEY] = canonicalCardioDraft
            state.copy(
                draft = changed,
                isDirty = true,
                validationResult = null,
                errorMessage = null,
            )
        }
    }

    // Safely update one exercise inside the workout draft
    private fun WorkoutDraft.updateExercise(
        index: Int,
        transform: (WorkoutExerciseDraft) -> WorkoutExerciseDraft,
    ): WorkoutDraft {
        if (index !in exercises.indices) return this
        return copy(exercises = exercises.mapIndexed { row, value -> if (row == index) transform(value) else value })
    }

    // Return whether the draft contains information which would be lost by clearing it
    private fun WorkoutDraft.hasMeaningfulContent(): Boolean =
        name.isNotBlank() || exercises.any { exercise ->
            exercise.catalogExerciseId != null ||
                exercise.sets.any { it.reps.isNotBlank() || it.weightKg.isNotBlank() } ||
                exercise.cardioEntry.minutes != "00" || exercise.cardioEntry.seconds != "00" ||
                exercise.cardioEntry.distanceMeters.isNotBlank()
        }

    // Copy a saved workout into a draft without converting its canonical kilogram values
    private fun Workout.toKilogramDraft() = WorkoutDraft(
        workoutId = id,
        name = name,
        date = date,
        exercises = exercises.map { exercise ->
            WorkoutExerciseDraft(
                catalogExerciseId = exercise.catalogExerciseId,
                name = exercise.name,
                sets = exercise.sets.map { set ->
                    ExerciseSetDraft(
                        reps = set.reps.toString(),
                        weightKg = set.weightKg.asInputText(),
                    )
                },
                cardioEntry = exercise.cardioEntry?.let {
                    CardioEntryDraft(
                        minutes = (it.durationSeconds / 60).toString().padStart(2, '0'),
                        seconds = (it.durationSeconds % 60).toString().padStart(2, '0'),
                        distanceMeters = it.distanceMeters?.asInputText().orEmpty(),
                    )
                } ?: CardioEntryDraft(),
            )
        }.ifEmpty { listOf(WorkoutExerciseDraft()) },
        type = type,
    )

    // Convert every valid weight while leaving unfinished or invalid input unchanged
    private fun WorkoutDraft.convertWeights(
        from: WeightsUnit,
        to: WeightsUnit,
        canonicalCardio: WorkoutDraft? = null,
    ): WorkoutDraft {
        val canonicalExercises = canonicalCardio?.exercises.orEmpty().associateBy { it.editorKey }
        return copy(exercises = exercises.map { exercise ->
            exercise.copy(sets = exercise.sets.map { set ->
                val value = set.weightKg.toDoubleOrNull()
                set.copy(
                    weightKg = value?.let { to.format(to.fromKilograms(from.toKilograms(it))) }
                        ?: set.weightKg,
                )
            }, cardioEntry = exercise.cardioEntry.let { cardio ->
                val canonicalMeters = canonicalExercises[exercise.editorKey]
                    ?.cardioEntry?.distanceMeters?.toDoubleOrNull()
                val displayed = canonicalMeters?.let(to::formatMeters) ?: run {
                    val value = cardio.distanceMeters.toDoubleOrNull()
                    value?.let { to.format(to.fromMeters(from.toMeters(it))) } ?: cardio.distanceMeters
                }
                cardio.copy(distanceMeters = displayed)
            })
        })
    }

    private fun WorkoutDraft.withCanonicalDistances(from: WeightsUnit): WorkoutDraft =
        copy(exercises = exercises.map { exercise ->
            val distance = exercise.cardioEntry.distanceMeters
            exercise.copy(cardioEntry = exercise.cardioEntry.copy(
                distanceMeters = distance.toDoubleOrNull()?.let { from.toMeters(it).asInputText() } ?: distance,
            ))
        })

    private fun WorkoutDraft.withUpdatedCanonicalDistances(
        previousDisplay: WorkoutDraft,
        previousCanonical: WorkoutDraft?,
        from: WeightsUnit,
    ): WorkoutDraft {
        val previousDisplayExercises = previousDisplay.exercises.associateBy { it.editorKey }
        val previousCanonicalExercises = previousCanonical?.exercises.orEmpty().associateBy { it.editorKey }
        return copy(exercises = exercises.map { exercise ->
            val distance = exercise.cardioEntry.distanceMeters
            val previousDistance = previousDisplayExercises[exercise.editorKey]?.cardioEntry?.distanceMeters
            val retained = previousCanonicalExercises[exercise.editorKey]?.cardioEntry?.distanceMeters
                ?.takeIf { distance == previousDistance }
            exercise.copy(cardioEntry = exercise.cardioEntry.copy(
                distanceMeters = retained
                    ?: distance.toDoubleOrNull()?.let { from.toMeters(it).asInputText() }
                    ?: distance,
            ))
        })
    }

    private fun WorkoutDraft.withCanonicalDistancesDisplayed(): WorkoutDraft = copy(
        exercises = exercises.map { exercise ->
            val meters = exercise.cardioEntry.distanceMeters.toDoubleOrNull()
            exercise.copy(cardioEntry = exercise.cardioEntry.copy(
                distanceMeters = meters?.let(WeightsUnit.METRIC::formatMeters).orEmpty(),
            ))
        },
    )

    // Avoid adding trailing zeroes to converted editor values
    private fun Double.asInputText(): String =
        BigDecimal.valueOf(this).stripTrailingZeros().toPlainString()

    // Give a user-friendly generic error
    private fun Throwable.userMessage(): String = message ?: "Something went wrong. Please try again."

    private companion object {
        const val DRAFT_KEY = "workout_editor_draft"
        const val ORIGINAL_DRAFT_KEY = "workout_editor_original_draft"
        const val ORIGINAL_KILOGRAM_DRAFT_KEY = "workout_editor_original_kilogram_draft"
        const val CANONICAL_CARDIO_DRAFT_KEY = "workout_editor_canonical_cardio_draft"
        const val NOTE_EDITOR_KEY = "workout_note_editor"
        const val WEIGHTS_UNIT_KEY = "workout_editor_weights_unit"
    }
}

// Create a kilogram draft while retaining exact database values for untouched weight inputs
internal fun WorkoutDraft.toKilogramsPreservingUnchanged(
    from: WeightsUnit,
    originalDisplay: WorkoutDraft?,
    originalKilograms: WorkoutDraft?,
    canonicalCardio: WorkoutDraft? = null,
): WorkoutDraft {
    val originalDisplaySets = originalDisplay
        ?.exercises
        .orEmpty()
        .flatMap { it.sets }
        .associateBy { it.editorKey }
    val originalKilogramSets = originalKilograms
        ?.exercises
        .orEmpty()
        .flatMap { it.sets }
        .associateBy { it.editorKey }
    val originalDisplayExercises = originalDisplay?.exercises.orEmpty().associateBy { it.editorKey }
    val originalCanonicalExercises = originalKilograms?.exercises.orEmpty().associateBy { it.editorKey }
    val canonicalCardioExercises = canonicalCardio?.exercises.orEmpty().associateBy { it.editorKey }
    return copy(exercises = exercises.map { exercise ->
        exercise.copy(sets = exercise.sets.map { set ->
            val originalText = originalDisplaySets[set.editorKey]?.weightKg
            val exactKilograms = originalKilogramSets[set.editorKey]?.weightKg
            if (exactKilograms != null && set.weightKg == originalText) {
                set.copy(weightKg = exactKilograms)
            } else {
                val value = set.weightKg.toDoubleOrNull()
                set.copy(
                    weightKg = value?.let {
                        WeightsUnit.METRIC.format(from.toKilograms(it))
                    } ?: set.weightKg,
                )
            }
        }, cardioEntry = exercise.cardioEntry.let { cardio ->
            val originalText = originalDisplayExercises[exercise.editorKey]?.cardioEntry?.distanceMeters
            val exactMeters = originalCanonicalExercises[exercise.editorKey]?.cardioEntry?.distanceMeters
            val draftMeters = canonicalCardioExercises[exercise.editorKey]?.cardioEntry?.distanceMeters
            val value = cardio.distanceMeters.toDoubleOrNull()
            cardio.copy(distanceMeters = when {
                draftMeters?.toDoubleOrNull() != null -> draftMeters
                exactMeters != null && cardio.distanceMeters == originalText -> exactMeters
                else -> value?.let { WeightsUnit.METRIC.format(from.toMeters(it)) } ?: cardio.distanceMeters
            })
        })
    })
}
