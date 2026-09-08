package com.example.workouttracker.feature.settings

import com.example.workouttracker.core.model.AppNotificationType
import com.example.workouttracker.core.model.AppPreferences
import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.ExerciseType
import com.example.workouttracker.domain.repository.BackupConnectionState
import com.example.workouttracker.domain.repository.BackupRepository
import com.example.workouttracker.domain.repository.ExerciseRepository
import com.example.workouttracker.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

// Check that completed Settings actions emit the correct one-time notifications
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
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
    fun driveActionsEmitSuccessNotifications() = runTest(dispatcher) {
        val backup = FakeBackupRepository(BackupConnectionState.SignedOut)
        val model = createModel(backup = backup)
        advanceUntilIdle()

        assertNotification(model, "Google Drive connected.") { model.signInOrOut() }
        assertNotification(model, "Backup completed.") { model.confirmBackup() }
        assertNotification(model, "Backup restored.") { model.confirmRestore() }
        assertNotification(model, "Google Drive disconnected.") { model.signInOrOut() }

        assertEquals(1, backup.backupCalls)
        assertEquals(1, backup.restoreCalls)
        assertEquals(1, backup.signOutCalls)
    }

    @Test
    fun unavailableDriveDoesNotEmitSuccessOrInvokeAuthentication() = runTest(dispatcher) {
        val backup = FakeBackupRepository(BackupConnectionState.Unavailable)
        val model = createModel(backup = backup)
        advanceUntilIdle()

        val event = async { model.events.first() }
        advanceUntilIdle()
        model.signInOrOut()
        advanceUntilIdle()

        assertFalse(event.isCompleted)
        event.cancel()
        assertEquals(0, backup.signInCalls)
        assertEquals(0, backup.signOutCalls)
    }

    @Test
    fun exerciseRenameAndDeleteEmitSuccessNotifications() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository()
        val model = createModel(exercises = exercises)
        advanceUntilIdle()

        assertNotification(model, "Exercise renamed.") {
            model.renameExercise(1L, "Incline press")
        }
        assertNotification(model, "Exercise deleted.") { model.deleteExercise(1L) }

        assertEquals("Incline press", exercises.renamedTo)
        assertEquals(1L, exercises.deletedId)
    }

    @Test
    fun failedActionEmitsErrorNotificationInsteadOfSuccess() = runTest(dispatcher) {
        val exercises = FakeExerciseRepository(deleteError = IllegalStateException("Exercise is in use."))
        val model = createModel(exercises = exercises)
        advanceUntilIdle()

        val event = async { model.events.filterIsInstance<SettingsEvent.Notify>().first() }
        model.deleteExercise(1L)
        advanceUntilIdle()

        assertEquals(AppNotificationType.ERROR, event.await().notification.type)
        assertEquals("Exercise is in use.", event.await().notification.message)
    }

    @Test
    fun successfulRestoreEmitsDataRefreshEvent() = runTest(dispatcher) {
        val model = createModel()
        advanceUntilIdle()

        val restored = async {
            model.events.filterIsInstance<SettingsEvent.DataRestored>().first()
        }
        model.confirmRestore()
        advanceUntilIdle()

        assertEquals(SettingsEvent.DataRestored, restored.await())
    }

    // Collect before starting an action so SharedFlow cannot drop the one-time result
    private suspend fun kotlinx.coroutines.test.TestScope.assertNotification(
        model: SettingsViewModel,
        expectedMessage: String,
        action: () -> Unit,
    ) {
        val event = async { model.events.filterIsInstance<SettingsEvent.Notify>().first() }
        action()
        advanceUntilIdle()
        assertEquals(AppNotificationType.SUCCESS, event.await().notification.type)
        assertEquals(expectedMessage, event.await().notification.message)
    }

    private fun createModel(
        backup: FakeBackupRepository = FakeBackupRepository(BackupConnectionState.Connected),
        exercises: FakeExerciseRepository = FakeExerciseRepository(),
    ) = SettingsViewModel(FakePreferencesRepository(), exercises, backup)

    private class FakePreferencesRepository : PreferencesRepository {
        override val preferences = MutableStateFlow(AppPreferences())
        override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
            preferences.value = transform(preferences.value)
        }
        override suspend fun reset() {
            preferences.value = AppPreferences()
        }
    }

    private class FakeBackupRepository(initialState: BackupConnectionState) : BackupRepository {
        private val state = MutableStateFlow(initialState)
        override val connectionState: Flow<BackupConnectionState> = state
        var backupCalls = 0
        var restoreCalls = 0
        var signOutCalls = 0
        var signInCalls = 0

        override suspend fun signIn() {
            signInCalls++
            state.value = BackupConnectionState.Connected
        }
        override suspend fun signOut() {
            signOutCalls++
            state.value = BackupConnectionState.SignedOut
        }
        override suspend fun backup() {
            backupCalls++
        }
        override suspend fun restore() {
            restoreCalls++
        }
    }

    private class FakeExerciseRepository(
        private val deleteError: Throwable? = null,
    ) : ExerciseRepository {
        private val catalog = MutableStateFlow(listOf(CatalogExercise(1L, "Bench press")))
        var renamedTo: String? = null
        var deletedId: Long? = null

        override fun observeCatalog(): Flow<List<CatalogExercise>> = catalog
        override suspend fun addExercise(name: String, type: ExerciseType) = 2L
        override suspend fun renameExercise(exerciseId: Long, newName: String) {
            renamedTo = newName
        }
        override suspend fun deleteExercise(exerciseId: Long) {
            deleteError?.let { throw it }
            deletedId = exerciseId
        }
        override suspend fun combineExercises(sourceExerciseId: Long, targetExerciseId: Long) = Unit
        override suspend fun setExerciseNote(exerciseId: Long, note: String?) = Unit
        override fun observeWorkoutNameNote(workoutName: String): Flow<String?> = MutableStateFlow(null)
        override suspend fun setWorkoutNameNote(workoutName: String, note: String?) = Unit
    }
}
