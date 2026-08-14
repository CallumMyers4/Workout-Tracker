package com.example.workouttracker

import android.content.Context
import com.example.workouttracker.data.backup.DriveBackupRepository
import com.example.workouttracker.data.backup.GoogleDriveRestGateway
import com.example.workouttracker.data.backup.GoogleAuthorizationGateway
import com.example.workouttracker.data.backup.RoomCheckpoint
import com.example.workouttracker.data.local.WorkoutDatabase
import com.example.workouttracker.data.preferences.DataStorePreferencesRepository
import com.example.workouttracker.data.repository.RoomExerciseRepository
import com.example.workouttracker.data.repository.RoomGoalRepository
import com.example.workouttracker.data.repository.RoomWorkoutRepository
import com.example.workouttracker.domain.repository.BackupRepository
import com.example.workouttracker.domain.repository.ExerciseRepository
import com.example.workouttracker.domain.repository.GoalRepository
import com.example.workouttracker.domain.repository.PreferencesRepository
import com.example.workouttracker.domain.repository.WorkoutRepository
import com.example.workouttracker.domain.service.ProgressCalculator

// Create and share one instance of each database, repository, and backup service
class AppContainer(
    context: Context,
    authorizationGateway: GoogleAuthorizationGateway,
) {
    // Use the application context so these long-lived objects do not keep an Activity in memory
    private val appContext = context.applicationContext
    val database: WorkoutDatabase = WorkoutDatabase.create(appContext)
    val preferencesRepository: PreferencesRepository = DataStorePreferencesRepository(appContext)
    val exerciseRepository: ExerciseRepository = RoomExerciseRepository(database)
    val workoutRepository: WorkoutRepository = RoomWorkoutRepository(database)
    val goalRepository: GoalRepository = RoomGoalRepository(database, ProgressCalculator())
    val backupRepository: BackupRepository = DriveBackupRepository(
        authorizationGateway = authorizationGateway,
        driveGateway = GoogleDriveRestGateway(authorizationGateway),
        checkpoint = RoomCheckpoint(database, appContext),
    )
}
