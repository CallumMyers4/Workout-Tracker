package com.example.workouttracker.domain.repository

import com.example.workouttracker.core.model.AppPreferences
import kotlinx.coroutines.flow.Flow

// Requirements to create a user preference class
interface PreferencesRepository {
    // Observe current user preferences
    val preferences: Flow<AppPreferences>

    // Apply and save a change to current preferences
    suspend fun update(transform: (AppPreferences) -> AppPreferences)

    // Return all preferences to their defaults
    suspend fun reset()
}
