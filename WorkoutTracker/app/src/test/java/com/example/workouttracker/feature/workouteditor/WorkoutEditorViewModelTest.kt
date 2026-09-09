package com.example.workouttracker.feature.workouteditor

import androidx.lifecycle.SavedStateHandle
import com.example.workouttracker.core.model.*
import com.example.workouttracker.domain.repository.ExerciseRepository
import com.example.workouttracker.domain.repository.WorkoutRepository
import com.example.workouttracker.domain.service.WorkoutValidator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun failedSaveRemainsVisibleToReturningCollectorAndRetryClearsError() = runTest(dispatcher) {
        val workouts = DeferredWorkoutRepository()
        val model = WorkoutEditorViewModel(
            SavedStateHandle(), workouts, EmptyExerciseRepository(), WorkoutValidator(),
        )
        model.updateWorkoutName("Morning workout")
        model.createAndSelectExercise(0, "Squat")
        advanceUntilIdle()
        model.updateSet(0, 0, "8", "40")
        val draft = model.uiState.value.draft

        model.save()
        advanceUntilIdle()
        assertTrue(model.uiState.value.isSaving)

        // Finish the repository call while no destination collects state or events.
        workouts.result.completeExceptionally(IllegalStateException("Unable to save workout"))
        advanceUntilIdle()

        val returnedState = model.uiState.first()
        assertFalse(returnedState.isSaving)
        assertTrue(returnedState.isDirty)
        assertEquals(draft, returnedState.draft)
        assertEquals("Unable to save workout", returnedState.errorMessage)
        assertTrue(model.events.replayCache.isEmpty())

        workouts.result = CompletableDeferred()
        model.save()
        advanceUntilIdle()
        assertTrue(model.uiState.value.isSaving)
        assertNull(model.uiState.value.errorMessage)
        workouts.result.complete(1L)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isSaving)
        assertFalse(model.uiState.value.isDirty)
        assertNull(model.uiState.value.errorMessage)
    }

    @Test
    fun selectingTypeAndConfirmedBackReturnsToChooser() = runTest(dispatcher) {
        val model = WorkoutEditorViewModel(
            SavedStateHandle(), DeferredWorkoutRepository(), EmptyExerciseRepository(), WorkoutValidator(),
        )
        model.selectWorkoutType(WorkoutType.CARDIO)
        assertEquals(WorkoutType.CARDIO, model.uiState.value.selectedType)
        model.updateWorkoutName("Ride")
        model.requestBackToChooser()
        assertTrue(model.uiState.value.showClearConfirmation)
        model.confirmClear()
        assertNull(model.uiState.value.selectedType)
        assertFalse(model.uiState.value.isDirty)
    }

    @Test
    fun blankBackReturnsToChooserWithoutConfirmation() = runTest(dispatcher) {
        val model = WorkoutEditorViewModel(
            SavedStateHandle(), DeferredWorkoutRepository(), EmptyExerciseRepository(), WorkoutValidator(),
        )
        model.selectWorkoutType(WorkoutType.STRENGTH)
        model.requestBackToChooser()
        assertNull(model.uiState.value.selectedType)
        assertFalse(model.uiState.value.showClearConfirmation)
    }

    @Test
    fun newCardioDistanceRetainsCanonicalMetersAcrossUnitChangesAndRecreation() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val workouts = DeferredWorkoutRepository()
        val model = WorkoutEditorViewModel(
            savedState, workouts, EmptyExerciseRepository(), WorkoutValidator(),
        )
        model.selectWorkoutType(WorkoutType.CARDIO)
        model.updateWorkoutName("Morning run")
        model.createAndSelectExercise(0, "Running")
        advanceUntilIdle()
        // Four kilometres exposes the old two-decimal round-trip drift (4 -> 2.49 -> 4.01).
        model.updateCardioEntry(0, "25", "00", "4")

        model.setWeightsUnit(WeightsUnit.IMPERIAL)
        assertEquals("2.49", model.uiState.value.draft.exercises.single().cardioEntry.distanceMeters)

        // Recreate the destination as navigation does after visiting Settings.
        val restoredModel = WorkoutEditorViewModel(
            savedState, workouts, EmptyExerciseRepository(), WorkoutValidator(),
        )
        restoredModel.setWeightsUnit(WeightsUnit.METRIC)
        assertEquals("4", restoredModel.uiState.value.draft.exercises.single().cardioEntry.distanceMeters)

        restoredModel.save()
        advanceUntilIdle()

        assertEquals("4000", workouts.savedDraft?.exercises?.single()?.cardioEntry?.distanceMeters)
        workouts.result.complete(1L)
        advanceUntilIdle()
    }

    private class DeferredWorkoutRepository : WorkoutRepository {
        var result = CompletableDeferred<Long>()
        var savedDraft: WorkoutDraft? = null
        override suspend fun saveWorkout(draft: WorkoutDraft): Long {
            savedDraft = draft
            return result.await()
        }
        override fun observeWorkout(workoutId: Long): Flow<Workout?> = flowOf(null)
        override fun observeWorkoutSummaries(
            query: String,
            filter: WorkoutFilter,
            sort: WorkoutSort,
            grouping: WorkoutGrouping,
        ): Flow<List<WorkoutSummary>> = flowOf(emptyList())
        override suspend fun deleteWorkout(workoutId: Long) = Unit
    }

    private class EmptyExerciseRepository : ExerciseRepository {
        override fun observeCatalog(): Flow<List<CatalogExercise>> = flowOf(emptyList())
        override suspend fun addExercise(name: String, type: ExerciseType): Long = 1L
        override suspend fun renameExercise(exerciseId: Long, newName: String) = Unit
        override suspend fun deleteExercise(exerciseId: Long) = Unit
        override suspend fun combineExercises(sourceExerciseId: Long, targetExerciseId: Long) = Unit
        override suspend fun setExerciseNote(exerciseId: Long, note: String?) = Unit
        override fun observeWorkoutNameNote(workoutName: String): Flow<String?> = flowOf(null)
        override suspend fun setWorkoutNameNote(workoutName: String, note: String?) = Unit
    }
}
