package com.example.workouttracker.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workouttracker.core.model.AppPreferences
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.WorkoutTypeFilter
import com.example.workouttracker.data.preferences.DataStorePreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStorePreferencesRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: DataStorePreferencesRepository

    @Before
    fun resetPreferences() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        repository = DataStorePreferencesRepository(context)
        repository.reset()
    }

    @After
    fun clearPreferences() = runBlocking {
        repository.reset()
    }

    @Test
    fun everyPreferencePersistsAcrossRepositoryInstancesAndResetRestoresDefaults() = runBlocking {
        val expected = AppPreferences(
            darkTheme = true,
            searchText = "leg day",
            filter = WorkoutFilter.RECENT_90_DAYS,
            sort = WorkoutSort.OLDEST,
            grouping = WorkoutGrouping.WORKOUT_NAME,
            weightsUnit = WeightsUnit.IMPERIAL,
            workoutTypeFilter = WorkoutTypeFilter.CARDIO,
        )

        repository.update { expected }

        assertEquals(expected, repository.preferences.first())
        assertEquals(expected, DataStorePreferencesRepository(context).preferences.first())

        repository.reset()
        assertEquals(AppPreferences(), repository.preferences.first())
    }

    @Test
    fun concurrentPreferenceUpdatesDoNotLoseAnUnrelatedChange() = runBlocking {
        coroutineScope {
            listOf(
                async { repository.update { it.copy(darkTheme = true) } },
                async { repository.update { it.copy(weightsUnit = WeightsUnit.IMPERIAL) } },
                async { repository.update { it.copy(workoutTypeFilter = WorkoutTypeFilter.STRENGTH) } },
            ).awaitAll()
        }

        val stored = repository.preferences.first()
        assertTrue(stored.darkTheme)
        assertEquals(WeightsUnit.IMPERIAL, stored.weightsUnit)
        assertEquals(WorkoutTypeFilter.STRENGTH, stored.workoutTypeFilter)
    }
}
