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
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun editingWorkoutReplacesChildrenWithoutLeavingOrphans() = runBlocking {
        val benchId = exercises.addExercise("Bench press")
        val squatId = exercises.addExercise("Squat")
        val workoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Original workout",
                exercises = listOf(
                    WorkoutExerciseDraft(
                        catalogExerciseId = benchId,
                        name = "Bench press",
                        sets = listOf(
                            ExerciseSetDraft(reps = "5", weightKg = "80"),
                            ExerciseSetDraft(reps = "3", weightKg = "90"),
                        ),
                    ),
                ),
            ),
        )

        val returnedId = workouts.saveWorkout(
            WorkoutDraft(
                workoutId = workoutId,
                name = "Updated workout",
                exercises = listOf(
                    WorkoutExerciseDraft(
                        catalogExerciseId = squatId,
                        name = "Squat",
                        sets = listOf(ExerciseSetDraft(reps = "8", weightKg = "100")),
                    ),
                ),
            ),
        )

        assertEquals(workoutId, returnedId)
        val updated = workouts.observeWorkout(workoutId).first()
        assertEquals("Updated workout", updated?.name)
        assertEquals("Squat", updated?.exercises?.single()?.name)
        assertEquals(listOf(100.0), updated?.exercises?.single()?.sets?.map { it.weightKg })
        assertEquals(1, database.exerciseDao().getWorkoutExercises(workoutId).size)
        assertEquals(1, database.setDao().observeAll().first().size)
    }

    @Test
    fun failedWorkoutEditRollsBackTheCompleteOriginalGraph() = runBlocking {
        val benchId = exercises.addExercise("Bench press")
        val workoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Original workout",
                exercises = listOf(
                    WorkoutExerciseDraft(
                        catalogExerciseId = benchId,
                        name = "Bench press",
                        sets = listOf(ExerciseSetDraft(reps = "5", weightKg = "80")),
                    ),
                ),
            ),
        )

        val failedEdit = runCatching {
            workouts.saveWorkout(
                WorkoutDraft(
                    workoutId = workoutId,
                    name = "Partially updated workout",
                    exercises = listOf(
                        WorkoutExerciseDraft(
                            catalogExerciseId = benchId,
                            name = "Bench press",
                            sets = listOf(ExerciseSetDraft(reps = "3", weightKg = "95")),
                        ),
                        WorkoutExerciseDraft(
                            catalogExerciseId = Long.MAX_VALUE,
                            name = "Deleted exercise",
                            sets = listOf(ExerciseSetDraft(reps = "1", weightKg = "1")),
                        ),
                    ),
                ),
            )
        }

        assertTrue(failedEdit.isFailure)
        val unchanged = workouts.observeWorkout(workoutId).first()
        assertEquals("Original workout", unchanged?.name)
        assertEquals(listOf("Bench press"), unchanged?.exercises?.map { it.name })
        assertEquals(listOf(80.0), unchanged?.exercises?.single()?.sets?.map { it.weightKg })
    }

    @Test
    fun usedExerciseCanBeCombinedAndRetainsHistoryGoalAndNote() = runBlocking {
        val sourceId = exercises.addExercise("Bench press")
        val targetId = exercises.addExercise("Chest press")
        val goals = RoomGoalRepository(database, ProgressCalculator())
        goals.updateGoal(sourceId, 100.0)
        goals.updateGoal(targetId, 120.0)
        exercises.setExerciseNote(sourceId, "Source technique note")
        val workoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Push day",
                exercises = listOf(
                    WorkoutExerciseDraft(
                        catalogExerciseId = sourceId,
                        name = "Bench press",
                        sets = listOf(ExerciseSetDraft(reps = "5", weightKg = "90")),
                    ),
                ),
            ),
        )

        val rejectedDelete = runCatching { exercises.deleteExercise(sourceId) }
        assertTrue(rejectedDelete.isFailure)
        exercises.combineExercises(sourceId, targetId)

        val catalog = exercises.observeCatalog().first()
        assertEquals(listOf(targetId), catalog.map { it.id })
        assertEquals(120.0, catalog.single().goalKg ?: 0.0, 0.0)
        assertEquals("Source technique note", catalog.single().note)
        val historicalWorkout = workouts.observeWorkout(workoutId).first()
        assertEquals(targetId, historicalWorkout?.exercises?.single()?.catalogExerciseId)
        assertEquals("Chest press", historicalWorkout?.exercises?.single()?.name)
        assertEquals(90.0, historicalWorkout?.exercises?.single()?.sets?.single()?.weightKg ?: 0.0, 0.0)
    }

    @Test
    fun summarySearchDateFilteringAndSortingUsePersistedRoomData() = runBlocking {
        val benchId = exercises.addExercise("Bench press")
        val runningId = exercises.addExercise("Running", ExerciseType.CARDIO)
        val today = LocalDate.now()
        val oldWorkoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Old push day",
                date = today.minusDays(120),
                exercises = listOf(WorkoutExerciseDraft(
                    catalogExerciseId = benchId,
                    name = "Bench press",
                    sets = listOf(ExerciseSetDraft(editorKey = "old-set", reps = "5", weightKg = "80")),
                )),
            ),
        )
        val cardioWorkoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Morning cardio",
                date = today.minusDays(1),
                type = WorkoutType.CARDIO,
                exercises = listOf(WorkoutExerciseDraft(
                    catalogExerciseId = runningId,
                    name = "Running",
                    cardioEntry = CardioEntryDraft("20", "00", "5000"),
                )),
            ),
        )
        val recentWorkoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Recent push day",
                date = today,
                exercises = listOf(WorkoutExerciseDraft(
                    catalogExerciseId = benchId,
                    name = "Bench press",
                    sets = listOf(ExerciseSetDraft(editorKey = "recent-set", reps = "5", weightKg = "85")),
                )),
            ),
        )

        val recent = workouts.observeWorkoutSummaries(
            query = "",
            filter = WorkoutFilter.RECENT_30_DAYS,
            sort = WorkoutSort.OLDEST,
            grouping = WorkoutGrouping.NONE,
        ).first()
        assertEquals(listOf(cardioWorkoutId, recentWorkoutId), recent.map { it.id })

        val exerciseSearch = workouts.observeWorkoutSummaries(
            query = "running",
            filter = WorkoutFilter.ALL_TIME,
            sort = WorkoutSort.NEWEST,
            grouping = WorkoutGrouping.NONE,
        ).first()
        assertEquals(listOf(cardioWorkoutId), exerciseSearch.map { it.id })

        val allOldestFirst = workouts.observeWorkoutSummaries(
            query = "",
            filter = WorkoutFilter.ALL_TIME,
            sort = WorkoutSort.OLDEST,
            grouping = WorkoutGrouping.NONE,
        ).first()
        assertEquals(listOf(oldWorkoutId, cardioWorkoutId, recentWorkoutId), allOldestFirst.map { it.id })
    }

    @Test
    fun invalidBackupIsRejectedWithoutReplacingCurrentData() = runBlocking {
        val benchId = exercises.addExercise("Bench press")
        val workoutId = workouts.saveWorkout(
            WorkoutDraft(
                name = "Keep this workout",
                exercises = listOf(WorkoutExerciseDraft(
                    catalogExerciseId = benchId,
                    name = "Bench press",
                    sets = listOf(ExerciseSetDraft(reps = "5", weightKg = "80")),
                )),
            ),
        )
        val checkpoint = RoomCheckpoint(database, ApplicationProvider.getApplicationContext())
        val invalidBackup = checkpoint.temporaryFile("invalid").apply { writeText("not a database") }

        try {
            val restore = runCatching { checkpoint.restore(invalidBackup) }
            assertTrue(restore.isFailure)
            assertEquals("Keep this workout", workouts.observeWorkout(workoutId).first()?.name)
        } finally {
            invalidBackup.delete()
        }
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

    @Test
    fun cardioBackupRestorePreservesTypesGoalsEntriesAndRepositoryReads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidateName = "cardio-restore-candidate-${System.nanoTime()}.db"
        context.deleteDatabase(candidateName)
        val candidate = Room.databaseBuilder(context, WorkoutDatabase::class.java, candidateName)
            .allowMainThreadQueries()
            .build()
        try {
            val candidateExercises = RoomExerciseRepository(candidate)
            val candidateWorkouts = RoomWorkoutRepository(candidate)
            val candidateGoals = RoomGoalRepository(candidate, ProgressCalculator())
            val runningId = candidateExercises.addExercise("Restored running", ExerciseType.CARDIO)
            candidateGoals.updateCardioGoal(runningId, 5_000.0, 25 * 60L)
            candidateWorkouts.saveWorkout(
                WorkoutDraft(
                    name = "Restored 5K",
                    date = LocalDate.of(2026, 9, 8),
                    type = WorkoutType.CARDIO,
                    exercises = listOf(
                        WorkoutExerciseDraft(
                            catalogExerciseId = runningId,
                            name = "Restored running",
                            cardioEntry = CardioEntryDraft("24", "30", "5000"),
                        ),
                    ),
                ),
            )
        } finally {
            candidate.close()
        }

        try {
            RoomCheckpoint(database, context).restore(context.getDatabasePath(candidateName))

            val restoredExercise = exercises.observeCatalog().first().single()
            assertEquals(ExerciseType.CARDIO, restoredExercise.type)
            assertEquals(5_000.0, restoredExercise.cardioGoalDistanceMeters ?: 0.0, 0.0)
            assertEquals(25 * 60L, restoredExercise.cardioGoalDurationSeconds)

            val summary = workouts.observeWorkoutSummaries(
                query = "",
                filter = com.example.workouttracker.core.model.WorkoutFilter.ALL_TIME,
                sort = com.example.workouttracker.core.model.WorkoutSort.NEWEST,
                grouping = com.example.workouttracker.core.model.WorkoutGrouping.NONE,
            ).first().single()
            assertEquals(WorkoutType.CARDIO, summary.type)
            assertEquals(24 * 60L + 30L, summary.totalDurationSeconds)
            assertEquals(5_000.0, summary.totalDistanceMeters, 0.0)

            val restoredWorkout = workouts.observeWorkout(summary.id).first()
            assertEquals(WorkoutType.CARDIO, restoredWorkout?.type)
            assertEquals(24 * 60L + 30L, restoredWorkout?.exercises?.single()?.cardioEntry?.durationSeconds)
            assertEquals(5_000.0, restoredWorkout?.exercises?.single()?.cardioEntry?.distanceMeters ?: 0.0, 0.0)

            val restoredEntries = database.cardioDao().observeAll().first()
            assertEquals(1, restoredEntries.size)
            assertEquals(5_000.0, restoredEntries.single().distanceMeters ?: 0.0, 0.0)
        } finally {
            context.deleteDatabase(candidateName)
        }
    }

    @Test
    fun versionTwoBackupRestoresAsStrengthWithoutCardioEntries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val candidateName = "v2-restore-candidate-${System.nanoTime()}.db"
        context.deleteDatabase(candidateName)
        val candidateFile = context.getDatabasePath(candidateName)
        candidateFile.parentFile?.mkdirs()
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(candidateFile, null).use { candidate ->
            candidate.execSQL("CREATE TABLE workouts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, date TEXT NOT NULL)")
            candidate.execSQL("CREATE TABLE catalog_exercises (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, goalKg REAL, note TEXT)")
            candidate.execSQL("CREATE TABLE workout_exercises (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutId INTEGER NOT NULL, catalogExerciseId INTEGER NOT NULL, position INTEGER NOT NULL)")
            candidate.execSQL("CREATE TABLE exercise_sets (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workoutExerciseId INTEGER NOT NULL, position INTEGER NOT NULL, reps INTEGER NOT NULL, weightKg REAL NOT NULL)")
            candidate.execSQL("CREATE TABLE workout_name_notes (workoutName TEXT NOT NULL COLLATE NOCASE, note TEXT NOT NULL, PRIMARY KEY(workoutName))")
            candidate.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            candidate.execSQL("INSERT INTO catalog_exercises (id, name, goalKg, note) VALUES (1, 'Restored squat', 120.0, 'Brace')")
            candidate.execSQL("INSERT INTO workouts (id, name, date) VALUES (1, 'Restored strength', '2026-09-07')")
            candidate.execSQL("INSERT INTO workout_exercises (id, workoutId, catalogExerciseId, position) VALUES (1, 1, 1, 0)")
            candidate.execSQL("INSERT INTO exercise_sets (id, workoutExerciseId, position, reps, weightKg) VALUES (1, 1, 0, 5, 100.0)")
            candidate.execSQL("PRAGMA user_version = 2")
        }

        try {
            RoomCheckpoint(database, context).restore(candidateFile)

            val restoredExercise = exercises.observeCatalog().first().single()
            assertEquals(ExerciseType.STRENGTH, restoredExercise.type)
            assertEquals(120.0, restoredExercise.goalKg ?: 0.0, 0.0)
            assertNull(restoredExercise.cardioGoalDistanceMeters)
            val restoredWorkout = workouts.observeWorkout(1L).first()
            assertNotNull(restoredWorkout)
            assertEquals(WorkoutType.STRENGTH, restoredWorkout?.type)
            assertEquals(100.0, restoredWorkout?.exercises?.single()?.sets?.single()?.weightKg ?: 0.0, 0.0)
            assertEquals(emptyList<Any>(), database.cardioDao().observeAll().first())
        } finally {
            context.deleteDatabase(candidateName)
        }
    }
}
