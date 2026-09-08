package com.example.workouttracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.workouttracker.data.local.WorkoutDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.room.execSQL
import androidx.room.PooledConnection
import com.example.workouttracker.data.local.entity.CatalogExerciseEntity
import com.example.workouttracker.data.local.entity.ExerciseSetEntity
import com.example.workouttracker.data.local.entity.WorkoutEntity
import com.example.workouttracker.data.local.entity.WorkoutExerciseEntity
import com.example.workouttracker.data.local.entity.WorkoutNameNoteEntity
import java.time.LocalDate

// Requirements for creating, checking, and restoring a complete database checkpoint
interface DatabaseCheckpoint {
    // Create a temporary location for a checkpoint
    fun temporaryFile(prefix: String): File
    // Copy the active database into a checkpoint
    suspend fun create(destination: File): File
    // Confirm that a checkpoint can safely be restored
    suspend fun validate(candidate: File)
    // Replace local tables with a checkpoint
    suspend fun restore(candidate: File)
}

// Create and restore checkpoints while keeping the active Room database open
class RoomCheckpoint(
    private val database: WorkoutDatabase,
    context: Context,
) : DatabaseCheckpoint {
    private val appContext = context.applicationContext

    // Create a temporary database file inside the app's cache
    override fun temporaryFile(prefix: String): File =
        File.createTempFile("workout-$prefix-", ".db", appContext.cacheDir)

    // Flush committed writes and copy the complete database into a checkpoint
    override suspend fun create(destination: File): File {
        return withContext(Dispatchers.IO) {
            // Move committed WAL pages into the main database while allowing normal readers
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
                check(it.moveToFirst()) { "Unable to checkpoint the workout database." }
            }
            val source = appContext.getDatabasePath(WorkoutDatabase.DATABASE_NAME)
            check(source.isFile) { "The workout database does not exist yet." }
            destination.parentFile?.mkdirs()
            source.inputStream().use { input -> destination.outputStream().use(input::copyTo) }
            destination
        }
    }

    // Confirm that a downloaded file is a compatible and undamaged Workout Tracker database
    override suspend fun validate(candidate: File) {
        withContext(Dispatchers.IO) {
            require(candidate.isFile && candidate.length() >= SQLITE_HEADER.length) {
                "The selected backup is empty or missing."
            }
            val header = ByteArray(SQLITE_HEADER.length)
            candidate.inputStream().use { input ->
                require(input.read(header) == header.size && header.decodeToString() == SQLITE_HEADER) {
                    "The selected file is not a SQLite database."
                }
            }
            val candidateDatabase = SQLiteDatabase.openDatabase(
                candidate.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            candidateDatabase.use { sqlite ->
                val version = sqlite.rawQuery("PRAGMA user_version", null).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
                require(version == 2) { "This backup uses an unsupported database version." }

                val tables = sqlite.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table'",
                    null,
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
                require(tables.containsAll(REQUIRED_TABLES)) {
                    "This is not a Workout Tracker Android backup."
                }
                val integrity = sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getString(0)
                }
                require(integrity.equals("ok", ignoreCase = true)) {
                    "The backup database is damaged."
                }
            }
        }
    }

    // Replace every local table from a previously validated checkpoint
    override suspend fun restore(candidate: File) {
        validate(candidate)
        val snapshot = readSnapshot(candidate)
        // Write the validated snapshot through one Room-managed connection and transaction
        database.useWriterConnection { connection ->
            // Use one transaction so screens never observe a partly restored database
            connection.immediateTransaction {
                execSQL("DELETE FROM exercise_sets")
                execSQL("DELETE FROM workout_exercises")
                execSQL("DELETE FROM workouts")
                execSQL("DELETE FROM workout_name_notes")
                execSQL("DELETE FROM catalog_exercises")
                insertSnapshot(snapshot)
            }
        }
    }

    // Read and parse backup rows off the caller's dispatcher before opening the Room writer transaction
    private suspend fun readSnapshot(candidate: File): BackupSnapshot = withContext(Dispatchers.IO) {
        val sqlite = SQLiteDatabase.openDatabase(
            candidate.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        sqlite.use { source ->
            BackupSnapshot(
                catalog = source.rawQuery("SELECT id, name, goalKg, note FROM catalog_exercises", null)
                    .use { cursor ->
                        buildList {
                            while (cursor.moveToNext()) {
                                add(
                                    CatalogExerciseEntity(
                                        id = cursor.getLong(0),
                                        name = cursor.getString(1),
                                        goalKg = if (cursor.isNull(2)) null else cursor.getDouble(2),
                                        note = if (cursor.isNull(3)) null else cursor.getString(3),
                                    ),
                                )
                            }
                        }
                    },
                workouts = source.rawQuery("SELECT id, name, date FROM workouts", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(WorkoutEntity(cursor.getLong(0), cursor.getString(1), LocalDate.parse(cursor.getString(2))))
                        }
                    }
                },
                workoutExercises = source.rawQuery(
                    "SELECT id, workoutId, catalogExerciseId, position FROM workout_exercises",
                    null,
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(WorkoutExerciseEntity(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2), cursor.getInt(3)))
                        }
                    }
                },
                sets = source.rawQuery(
                    "SELECT id, workoutExerciseId, position, reps, weightKg FROM exercise_sets",
                    null,
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(ExerciseSetEntity(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2), cursor.getInt(3), cursor.getDouble(4)))
                        }
                    }
                },
                notes = source.rawQuery("SELECT workoutName, note FROM workout_name_notes", null).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(WorkoutNameNoteEntity(cursor.getString(0), cursor.getString(1)))
                    }
                },
            )
        }
    }

    // Insert all snapshot rows with their original IDs and relationships
    private suspend fun PooledConnection.insertSnapshot(snapshot: BackupSnapshot) {
        snapshot.catalog.forEach { row ->
            usePrepared("INSERT INTO catalog_exercises (id, name, goalKg, note) VALUES (?, ?, ?, ?)") {
                it.bindLong(1, row.id); it.bindText(2, row.name)
                if (row.goalKg == null) it.bindNull(3) else it.bindDouble(3, row.goalKg)
                if (row.note == null) it.bindNull(4) else it.bindText(4, row.note)
                it.step()
            }
        }
        snapshot.workouts.forEach { row ->
            usePrepared("INSERT INTO workouts (id, name, date) VALUES (?, ?, ?)") {
                it.bindLong(1, row.id); it.bindText(2, row.name); it.bindText(3, row.date.toString()); it.step()
            }
        }
        snapshot.workoutExercises.forEach { row ->
            usePrepared("INSERT INTO workout_exercises (id, workoutId, catalogExerciseId, position) VALUES (?, ?, ?, ?)") {
                it.bindLong(1, row.id); it.bindLong(2, row.workoutId); it.bindLong(3, row.catalogExerciseId)
                it.bindLong(4, row.position.toLong()); it.step()
            }
        }
        snapshot.sets.forEach { row ->
            usePrepared("INSERT INTO exercise_sets (id, workoutExerciseId, position, reps, weightKg) VALUES (?, ?, ?, ?, ?)") {
                it.bindLong(1, row.id); it.bindLong(2, row.workoutExerciseId); it.bindLong(3, row.position.toLong())
                it.bindLong(4, row.reps.toLong()); it.bindDouble(5, row.weightKg); it.step()
            }
        }
        snapshot.notes.forEach { row ->
            usePrepared("INSERT INTO workout_name_notes (workoutName, note) VALUES (?, ?)") {
                it.bindText(1, row.workoutName); it.bindText(2, row.note); it.step()
            }
        }
    }

    private data class BackupSnapshot(
        val catalog: List<CatalogExerciseEntity>,
        val workouts: List<WorkoutEntity>,
        val workoutExercises: List<WorkoutExerciseEntity>,
        val sets: List<ExerciseSetEntity>,
        val notes: List<WorkoutNameNoteEntity>,
    )

    private companion object {
        const val SQLITE_HEADER = "SQLite format 3\u0000"
        val REQUIRED_TABLES = setOf(
            "workouts",
            "catalog_exercises",
            "workout_exercises",
            "exercise_sets",
            "workout_name_notes",
            "room_master_table",
        )
    }
}
