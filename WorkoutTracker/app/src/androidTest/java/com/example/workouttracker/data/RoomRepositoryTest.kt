package com.example.workouttracker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workouttracker.core.model.ExerciseSetDraft
import com.example.workouttracker.core.model.WorkoutDraft
import com.example.workouttracker.core.model.WorkoutExerciseDraft
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.core.model.ExerciseType
import com.example.workouttracker.core.model.CardioEntryDraft
import com.example.workouttracker.data.local.WorkoutDatabase
import com.example.workouttracker.data.local.DEFAULT_EXERCISE_CALLBACK
import com.example.workouttracker.data.repository.RoomExerciseRepository
import com.example.workouttracker.data.repository.RoomWorkoutRepository
import com.example.workouttracker.data.repository.RoomGoalRepository
import com.example.workouttracker.domain.service.ProgressCalculator
import com.example.workouttracker.data.backup.RoomCheckpoint
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

    @Test
    fun newDatabaseContainsDefaultStrengthAndCardioExercisesWithoutDuplicates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "default-exercises-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)

        fun openDatabase() = Room.databaseBuilder(context, WorkoutDatabase::class.java, databaseName)
            .addCallback(DEFAULT_EXERCISE_CALLBACK)
            .allowMainThreadQueries()
            .build()

        val expected = listOf(
            "Barbell Row" to ExerciseType.STRENGTH,
            "Bench Press" to ExerciseType.STRENGTH,
            "Bicep Curl" to ExerciseType.STRENGTH,
            "Cycling" to ExerciseType.CARDIO,
            "Deadlift" to ExerciseType.STRENGTH,
            "Lat Pulldown" to ExerciseType.STRENGTH,
            "Leg Press" to ExerciseType.STRENGTH,
            "Overhead Press" to ExerciseType.STRENGTH,
            "Running" to ExerciseType.CARDIO,
            "Squat" to ExerciseType.STRENGTH,
            "Tricep Extension" to ExerciseType.STRENGTH,
            "Walking" to ExerciseType.CARDIO,
        )

        val seededDatabase = openDatabase()
        try {
            try {
                assertEquals(expected, seededDatabase.exerciseDao().getCatalog().map { it.name to ExerciseType.valueOf(it.type) })
            } finally {
                seededDatabase.close()
            }

            val reopenedDatabase = openDatabase()
            try {
                assertEquals(expected, reopenedDatabase.exerciseDao().getCatalog().map { it.name to ExerciseType.valueOf(it.type) })
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun cardioWorkoutRoundTripsAndUsesOneEntryPerExercise() = runBlocking {
        val bikeId = exercises.addExercise("Bike", ExerciseType.CARDIO)
        val workoutId = workouts.saveWorkout(WorkoutDraft(
            name = "Morning ride",
            type = WorkoutType.CARDIO,
            exercises = listOf(WorkoutExerciseDraft(
                catalogExerciseId = bikeId,
                name = "Bike",
                cardioEntry = CardioEntryDraft("45", "30", "20000"),
            )),
        ))

        val saved = workouts.observeWorkout(workoutId).first()
        assertEquals(WorkoutType.CARDIO, saved?.type)
        assertEquals(2730L, saved?.exercises?.single()?.cardioEntry?.durationSeconds)
        assertEquals(20000.0, saved?.exercises?.single()?.cardioEntry?.distanceMeters)
        assertEquals(emptyList<Any>(), saved?.exercises?.single()?.sets)
    }

    @Test
    fun cardioGoalUsesBestQualifyingAveragePace() = runBlocking {
        val bikeId = exercises.addExercise("Bike", ExerciseType.CARDIO)
        workouts.saveWorkout(WorkoutDraft(
            name = "Five kilometre ride", type = WorkoutType.CARDIO,
            exercises = listOf(WorkoutExerciseDraft(
                catalogExerciseId = bikeId, name = "Bike",
                cardioEntry = CardioEntryDraft("30", "00", "5000"),
            )),
        ))
        val goals = RoomGoalRepository(database, ProgressCalculator())
        goals.updateCardioGoal(bikeId, 5000.0, 25 * 60L)

        val progress = goals.observeCardioProgress().first().single()
        assertEquals(5000.0, progress.longestDistanceMeters)
        assertEquals(1800L, progress.bestQualifying?.durationSeconds)
        assertEquals(83.333, progress.percentage ?: 0.0, 0.01)
    }

    @Test
    // Restore into an observed Room database and immediately open the restored workout
    fun restoredWorkoutCanBeListedAndOpenedImmediately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidateName = "restore-candidate-${System.nanoTime()}.db"
        context.deleteDatabase(candidateName)
        val candidate = Room.databaseBuilder(context, WorkoutDatabase::class.java, candidateName)
            .allowMainThreadQueries()
            .build()
        try {
            val candidateExercises = RoomExerciseRepository(candidate)
            val candidateWorkouts = RoomWorkoutRepository(candidate)
            val benchId = candidateExercises.addExercise("Restored bench press")
            candidateWorkouts.saveWorkout(
                WorkoutDraft(
                    name = "Restored workout",
                    date = LocalDate.of(2026, 9, 1),
                    exercises = listOf(
                        WorkoutExerciseDraft(
                            catalogExerciseId = benchId,
                            name = "Restored bench press",
                            sets = listOf(ExerciseSetDraft(reps = "5", weightKg = "80")),
                        ),
                    ),
                ),
            )
        } finally {
            candidate.close()
        }

        try {
            // Start Room observation before restoring to exercise its invalidation lifecycle
            workouts.observeWorkoutSummaries(
                query = "",
                filter = com.example.workouttracker.core.model.WorkoutFilter.ALL_TIME,
                sort = com.example.workouttracker.core.model.WorkoutSort.NEWEST,
                grouping = com.example.workouttracker.core.model.WorkoutGrouping.NONE,
            ).first()
            RoomCheckpoint(database, context).restore(context.getDatabasePath(candidateName))

            val summaries = workouts.observeWorkoutSummaries(
                query = "",
                filter = com.example.workouttracker.core.model.WorkoutFilter.ALL_TIME,
                sort = com.example.workouttracker.core.model.WorkoutSort.NEWEST,
                grouping = com.example.workouttracker.core.model.WorkoutGrouping.NONE,
            ).first()
            val restored = workouts.observeWorkout(summaries.single().id).first()

            assertEquals("Restored workout", restored?.name)
            assertEquals("Restored bench press", restored?.exercises?.single()?.name)
        } finally {
            context.deleteDatabase(candidateName)
        }
    }
}
