package com.example.workouttracker.feature.workoutlist

import com.example.workouttracker.core.model.AppPreferences
import com.example.workouttracker.core.model.Workout
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.WorkoutTypeFilter
import com.example.workouttracker.domain.repository.PreferencesRepository
import com.example.workouttracker.domain.repository.WorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Check explicit Home refresh behavior used after restoring a backup
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshRestartsWorkoutRepositoryObservation() = runTest(dispatcher) {
        val workouts = FakeWorkoutRepository()
        val model = WorkoutListViewModel(workouts, FakePreferencesRepository())
        advanceUntilIdle()
        assertEquals(2, workouts.observationCount)

        model.refresh()
        advanceUntilIdle()

        assertEquals(4, workouts.observationCount)
    }

    @Test
    fun workoutTypeFiltersPreserveMatchingPagingCounts() = runTest(dispatcher) {
        val summaries = listOf(
            summary(1L, "Upper body", LocalDate.of(2026, 9, 12), WorkoutType.STRENGTH),
            summary(2L, "Morning run", LocalDate.of(2026, 9, 11), WorkoutType.CARDIO),
            summary(3L, "Lower body", LocalDate.of(2026, 9, 10), WorkoutType.STRENGTH),
            summary(4L, "Evening ride", LocalDate.of(2026, 9, 9), WorkoutType.CARDIO),
        )
        val preferences = FakePreferencesRepository()
        val model = WorkoutListViewModel(FakeWorkoutRepository(summaries), preferences)
        advanceUntilIdle()

        assertVisibleTypes(model, listOf(WorkoutType.STRENGTH, WorkoutType.CARDIO, WorkoutType.STRENGTH, WorkoutType.CARDIO))

        model.setTypeFilter(WorkoutTypeFilter.STRENGTH)
        advanceUntilIdle()
        assertVisibleTypes(model, listOf(WorkoutType.STRENGTH, WorkoutType.STRENGTH))

        model.setTypeFilter(WorkoutTypeFilter.CARDIO)
        advanceUntilIdle()
        assertVisibleTypes(model, listOf(WorkoutType.CARDIO, WorkoutType.CARDIO))

        model.setTypeFilter(WorkoutTypeFilter.ALL)
        advanceUntilIdle()
        assertVisibleTypes(model, listOf(WorkoutType.STRENGTH, WorkoutType.CARDIO, WorkoutType.STRENGTH, WorkoutType.CARDIO))
    }

    private fun assertVisibleTypes(model: WorkoutListViewModel, expected: List<WorkoutType>) {
        val state = model.uiState.value
        assertEquals(expected, state.workouts.map { it.type })
        assertEquals(expected.size, state.loadedItemCount)
        assertEquals(expected.size, state.totalItemCount)
    }

    private fun summary(id: Long, name: String, date: LocalDate, type: WorkoutType) = WorkoutSummary(
        id = id,
        name = name,
        date = date,
        exerciseCount = 1,
        exerciseNames = listOf(name),
        type = type,
    )

    private class FakePreferencesRepository : PreferencesRepository {
        override val preferences = MutableStateFlow(AppPreferences())
        override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
            preferences.value = transform(preferences.value)
        }
        override suspend fun reset() {
            preferences.value = AppPreferences()
        }
    }

    private class FakeWorkoutRepository(initialSummaries: List<WorkoutSummary> = emptyList()) : WorkoutRepository {
        private val summaries = MutableStateFlow(initialSummaries)
        var observationCount = 0

        override fun observeWorkoutSummaries(
            query: String,
            filter: WorkoutFilter,
            sort: WorkoutSort,
            grouping: WorkoutGrouping,
        ): Flow<List<WorkoutSummary>> {
            observationCount++
            return summaries
        }
        override fun observeWorkout(workoutId: Long): Flow<Workout?> = MutableStateFlow(null)
        override suspend fun saveWorkout(draft: WorkoutDraft) = 1L
        override suspend fun deleteWorkout(workoutId: Long) = Unit
    }
}
