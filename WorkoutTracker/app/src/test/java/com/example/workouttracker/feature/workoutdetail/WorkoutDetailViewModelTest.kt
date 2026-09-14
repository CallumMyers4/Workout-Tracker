package com.example.workouttracker.feature.workoutdetail

import androidx.lifecycle.SavedStateHandle
import com.example.workouttracker.core.model.AppNotificationType
import com.example.workouttracker.core.model.Workout
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.domain.repository.WorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun confirmedDeleteRemovesWorkoutAndEmitsSuccessThenDeleted() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository()
        val model = WorkoutDetailViewModel(SavedStateHandle(mapOf("workoutId" to WORKOUT_ID)), repository)
        advanceUntilIdle()

        model.requestDelete()
        assertTrue(model.uiState.value.showDeleteConfirmation)
        model.cancelDelete()
        assertFalse(model.uiState.value.showDeleteConfirmation)
        model.requestDelete()

        val events = async { model.events.take(2).toList() }
        advanceUntilIdle()
        model.confirmDelete()
        advanceUntilIdle()

        assertEquals(listOf(WORKOUT_ID), repository.deletedIds)
        val notification = events.await()[0] as WorkoutDetailEvent.Notify
        assertEquals(AppNotificationType.SUCCESS, notification.notification.type)
        assertEquals("Workout deleted.", notification.notification.message)
        assertEquals(WorkoutDetailEvent.Deleted, events.await()[1])
    }

    @Test
    fun failedDeleteKeepsWorkoutVisibleAndReportsError() = runTest(dispatcher) {
        val repository = FakeWorkoutRepository(IllegalStateException("Unable to delete workout"))
        val model = WorkoutDetailViewModel(SavedStateHandle(mapOf("workoutId" to WORKOUT_ID)), repository)
        advanceUntilIdle()

        val event = async { model.events.take(1).toList().single() }
        advanceUntilIdle()
        model.confirmDelete()
        advanceUntilIdle()

        assertFalse(model.uiState.value.isDeleting)
        assertEquals("Unable to delete workout", model.uiState.value.errorMessage)
        assertEquals("Push day", model.uiState.value.workout?.name)
        val notification = event.await() as WorkoutDetailEvent.Notify
        assertEquals(AppNotificationType.ERROR, notification.notification.type)
    }

    private class FakeWorkoutRepository(
        private val deleteError: Throwable? = null,
    ) : WorkoutRepository {
        private val workout = MutableStateFlow(
            Workout(WORKOUT_ID, "Push day", LocalDate.of(2026, 9, 12), emptyList()),
        )
        val deletedIds = mutableListOf<Long>()

        override fun observeWorkout(workoutId: Long): Flow<Workout?> = workout
        override fun observeWorkoutSummaries(
            query: String,
            filter: WorkoutFilter,
            sort: WorkoutSort,
            grouping: WorkoutGrouping,
        ): Flow<List<WorkoutSummary>> = MutableStateFlow(emptyList())

        override suspend fun saveWorkout(draft: WorkoutDraft) = WORKOUT_ID
        override suspend fun deleteWorkout(workoutId: Long) {
            deletedIds += workoutId
            deleteError?.let { throw it }
        }
    }

    private companion object {
        const val WORKOUT_ID = 9L
    }
}
