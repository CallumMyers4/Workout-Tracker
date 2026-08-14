package com.example.workouttracker.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.workouttracker.core.model.AppPreferences
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// Use one DataStore file for every preference repository
private val Context.workoutPreferences by preferencesDataStore(name = "workout_preferences")

// Save and observe user preferences using Android DataStore
class DataStorePreferencesRepository(context: Context) : PreferencesRepository {
    private val dataStore = context.applicationContext.workoutPreferences

    // Observe stored preferences and use defaults if the file cannot be read
    override val preferences: Flow<AppPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::toAppPreferences)

    // Apply and save a change to the current preferences
    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        dataStore.edit { stored ->
            write(stored, transform(toAppPreferences(stored)))
        }
    }

    // Go back to default settings
    override suspend fun reset() {
        dataStore.edit { stored ->
            stored.clear()
            write(stored, AppPreferences())
        }
    }

    // Convert stored preference values into the model used by the app
    private fun toAppPreferences(values: Preferences) = AppPreferences(
        darkTheme = values[DARK_THEME] ?: false,
        searchText = values[SEARCH_TEXT].orEmpty(),
        filter = values[FILTER].toEnumOrDefault(WorkoutFilter.ALL_TIME),
        sort = values[SORT].toEnumOrDefault(WorkoutSort.NEWEST),
        grouping = values[GROUPING].toEnumOrDefault(WorkoutGrouping.NONE),
    )

    // Write all preference model values into DataStore
    private fun write(values: androidx.datastore.preferences.core.MutablePreferences, preferences: AppPreferences) {
        values[DARK_THEME] = preferences.darkTheme
        values[SEARCH_TEXT] = preferences.searchText
        // Store enum names so new options can be added without changing saved number values
        values[FILTER] = preferences.filter.name
        values[SORT] = preferences.sort.name
        values[GROUPING] = preferences.grouping.name
    }

    // Return a stored enum value or its default when it is missing or no longer exists
    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    private companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SEARCH_TEXT = stringPreferencesKey("search_text")
        val FILTER = stringPreferencesKey("workout_filter")
        val SORT = stringPreferencesKey("workout_sort")
        val GROUPING = stringPreferencesKey("workout_grouping")
    }
}
