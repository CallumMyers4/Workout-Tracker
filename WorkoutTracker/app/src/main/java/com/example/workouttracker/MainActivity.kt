package com.example.workouttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.workouttracker.ui.WorkoutTrackerApp
import com.example.workouttracker.ui.theme.WorkoutTrackerTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workouttracker.data.backup.AndroidGoogleAuthorizationGateway

// Start the Android app and create the dependencies needed by the Compose interface
class MainActivity : ComponentActivity() {
    // Create the app when Android starts this Activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Create Google authorization here because it needs an Activity which is ready to use
        val authorizationGateway = AndroidGoogleAuthorizationGateway(this)
        val container = AppContainer(applicationContext, authorizationGateway)
        enableEdgeToEdge()
        setContent {
            // Observe the theme preference so the whole app updates immediately when it changes
            val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = com.example.workouttracker.core.model.AppPreferences(),
            )
            WorkoutTrackerTheme(darkTheme = preferences.darkTheme) {
                WorkoutTrackerApp(container = container)
            }
        }
    }
}
