package com.example.workouttracker.feature.goals

import com.example.workouttracker.core.model.CardioProgress
import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.ExerciseProgress
import com.example.workouttracker.core.model.ExerciseType
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.domain.repository.GoalRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
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
    fun clearingCardioGoalSavesNullDistanceAndDuration() = runTest(dispatcher) {
        val repository = FakeGoalRepository()
        val model = createCardioModel(repository)

        model.updateGoalInput("")
        model.updateGoalMinutes("0")
        model.updateGoalSeconds("0")
        model.saveGoal()
        advanceUntilIdle()

        assertEquals(listOf(CardioGoalUpdate(EXERCISE_ID, null, null)), repository.cardioUpdates)
        assertNull(model.uiState.value.editor)
    }

    @Test
    fun cardioGoalWithOnlyDistanceOrTimeIsRejected() = runTest(dispatcher) {
        val repository = FakeGoalRepository()
        val model = createCardioModel(repository)

        model.updateGoalInput("5")
        model.updateGoalMinutes("0")
        model.updateGoalSeconds("0")
        model.saveGoal()

        assertEquals("Enter positive time", model.uiState.value.editor?.timeError)
        assertEquals(emptyList<CardioGoalUpdate>(), repository.cardioUpdates)

        model.updateGoalInput("")
        model.updateGoalMinutes("20")
        model.saveGoal()

        assertEquals("Enter positive distance", model.uiState.value.editor?.distanceError)
        assertEquals(emptyList<CardioGoalUpdate>(), repository.cardioUpdates)
    }

    @Test
    fun imperialCardioGoalConvertsDistanceToMetersBeforeSaving() = runTest(dispatcher) {
        val repository = FakeGoalRepository()
        val model = createCardioModel(repository)
        model.setWeightsUnit(WeightsUnit.IMPERIAL)

        model.updateGoalInput("3.1")
        model.updateGoalMinutes("24")
        model.updateGoalSeconds("30")
        model.saveGoal()
        advanceUntilIdle()

        val update = repository.cardioUpdates.single()
        assertEquals(EXERCISE_ID, update.exerciseId)
        assertEquals(3.1 * 1609.344, update.distanceMeters!!, 0.0001)
        assertEquals(24 * 60L + 30L, update.durationSeconds)
        assertNull(model.uiState.value.editor)
    }

    @Test
    fun failedCardioGoalUpdateRetainsEditorAndDisplaysRepositoryError() = runTest(dispatcher) {
        val repository = FakeGoalRepository(updateError = IllegalStateException("Could not save goal."))
        val model = createCardioModel(repository)

        model.updateGoalInput("5")
        model.updateGoalMinutes("25")
        model.updateGoalSeconds("0")
        model.saveGoal()
        advanceUntilIdle()

        assertEquals(
            listOf(CardioGoalUpdate(EXERCISE_ID, 5000.0, 25 * 60L)),
            repository.cardioUpdates,
        )
        val editor = model.uiState.value.editor
        assertNotNull(editor)
        assertEquals("5", editor?.input)
        assertEquals("25", editor?.durationMinutes)
        assertEquals("0", editor?.durationSeconds)
        assertEquals("Could not save goal.", editor?.errorMessage)
    }

    @Test
    fun imperialStrengthGoalIsConvertedToKilogramsAndCanBeCleared() = runTest(dispatcher) {
        val repository = FakeGoalRepository()
        val model = GoalsViewModel(repository)
        advanceUntilIdle()
        model.openGoalEditor(STRENGTH_EXERCISE_ID)
        model.setWeightsUnit(WeightsUnit.IMPERIAL)
        assertEquals("220.46", model.uiState.value.editor?.input)

        model.updateGoalInput("242.51")
        model.saveGoal()
        advanceUntilIdle()
        assertEquals(110.0, repository.strengthUpdates.single().goalKg ?: 0.0, 0.01)
        assertNull(model.uiState.value.editor)

        model.openGoalEditor(STRENGTH_EXERCISE_ID)
        model.updateGoalInput("")
        model.saveGoal()
        advanceUntilIdle()
        assertEquals(StrengthGoalUpdate(STRENGTH_EXERCISE_ID, null), repository.strengthUpdates.last())
    }

    private suspend fun kotlinx.coroutines.test.TestScope.createCardioModel(
        repository: FakeGoalRepository,
    ): GoalsViewModel {
        val model = GoalsViewModel(repository)
        advanceUntilIdle()
        model.openGoalEditor(EXERCISE_ID)
        assertNotNull(model.uiState.value.editor)
        return model
    }

    private data class CardioGoalUpdate(
        val exerciseId: Long,
        val distanceMeters: Double?,
        val durationSeconds: Long?,
    )

    private data class StrengthGoalUpdate(val exerciseId: Long, val goalKg: Double?)

    private class FakeGoalRepository(
        private val updateError: Throwable? = null,
    ) : GoalRepository {
        private val strengthProgress = MutableStateFlow(
            listOf(
                ExerciseProgress(
                    exercise = CatalogExercise(
                        id = STRENGTH_EXERCISE_ID,
                        name = "Deadlift",
                        goalKg = 100.0,
                    ),
                    bestSet = null,
                    percentage = null,
                ),
            ),
        )
        private val cardioProgress = MutableStateFlow(
            listOf(
                CardioProgress(
                    exercise = CatalogExercise(
                        id = EXERCISE_ID,
                        name = "Running",
                        type = ExerciseType.CARDIO,
                        cardioGoalDistanceMeters = 5_000.0,
                        cardioGoalDurationSeconds = 25 * 60L,
                    ),
                ),
            ),
        )
        val strengthUpdates = mutableListOf<StrengthGoalUpdate>()
        val cardioUpdates = mutableListOf<CardioGoalUpdate>()

        override fun observeProgress(): Flow<List<ExerciseProgress>> = strengthProgress

        override fun observeCardioProgress(): Flow<List<CardioProgress>> = cardioProgress

        override suspend fun updateGoal(exerciseId: Long, goalKg: Double?) {
            strengthUpdates += StrengthGoalUpdate(exerciseId, goalKg)
            updateError?.let { throw it }
        }

        override suspend fun updateCardioGoal(
            exerciseId: Long,
            distanceMeters: Double?,
            durationSeconds: Long?,
        ) {
            cardioUpdates += CardioGoalUpdate(exerciseId, distanceMeters, durationSeconds)
            updateError?.let { throw it }
        }
    }

    private companion object {
        const val EXERCISE_ID = 7L
        const val STRENGTH_EXERCISE_ID = 8L
    }
}
