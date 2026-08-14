package com.example.workouttracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutExerciseDraft
import com.example.workouttracker.data.local.WorkoutDatabase
import com.example.workouttracker.data.repository.RoomExerciseRepository
import com.example.workouttracker.data.repository.RoomWorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
// Check Room repositories using a temporary in-memory Android database
class RoomRepositoryTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var exercises: RoomExerciseRepository
    private lateinit var workouts: RoomWorkoutRepository

    @Before
    // Create a clean database and repositories before each test
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exercises = RoomExerciseRepository(database)
        workouts = RoomWorkoutRepository(database)
    }

    @After
    // Close and discard the temporary database after each test
    fun closeDatabase() = database.close()

    @Test
    // Confirm that workouts save in order and child rows are removed during deletion
    fun completeWorkoutGraphSavesInOrderAndCascadeDeletes() = runBlocking {
        val benchId = exercises.addExercise("Bench Press")
        val workoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Push",
                date = LocalDate.of(2026, 8, 14),
                exercises = listOf(
                    WorkoutExerciseDraft(
                        catalogExerciseId = benchId,
                        name = "Bench Press",
                        sets = listOf(
                            ExerciseSetDraft(reps = "5", weightKg = "80"),
                            ExerciseSetDraft(reps = "3", weightKg = "90.5"),
                        ),
                    ),
                ),
            ),
        )

        val saved = workouts.observeWorkout(workoutId).first()
        assertEquals("Push", saved?.name)
        assertEquals(listOf(80.0, 90.5), saved?.exercises?.single()?.sets?.map { it.weightKg })

        workouts.deleteWorkout(workoutId)
        assertNull(workouts.observeWorkout(workoutId).first())
        assertEquals(emptyList<Any>(), database.exerciseDao().getWorkoutExercises(workoutId))
    }

    @Test
    // Confirm that exercise names ignore case and workout notes share a name
    fun catalogNamesAreCaseInsensitiveAndWorkoutNotesShareNameScope() = runBlocking {
        exercises.addExercise("  Squat  ")
        val duplicate = runCatching { exercises.addExercise("sQUAT") }
        assertEquals(true, duplicate.isFailure)

        exercises.setWorkoutNameNote("Push Day", "  Shared note  ")
        assertEquals("Shared note", exercises.observeWorkoutNameNote("push day").first())
        exercises.setWorkoutNameNote("PUSH DAY", null)
        assertNull(exercises.observeWorkoutNameNote("push day").first())
    }
}
