package com.example.workouttracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.workouttracker.data.local.WorkoutDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

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
        withContext(Dispatchers.IO) {
            val sqlite = database.openHelper.writableDatabase
            // Use one transaction so screens never observe a partly restored database
            sqlite.execSQL("ATTACH DATABASE ? AS restored", arrayOf(candidate.absolutePath))
            try {
                database.withTransaction {
                    sqlite.execSQL("DELETE FROM exercise_sets")
                    sqlite.execSQL("DELETE FROM workout_exercises")
                    sqlite.execSQL("DELETE FROM workouts")
                    sqlite.execSQL("DELETE FROM workout_name_notes")
                    sqlite.execSQL("DELETE FROM catalog_exercises")
                    sqlite.execSQL("INSERT INTO catalog_exercises SELECT * FROM restored.catalog_exercises")
                    sqlite.execSQL("INSERT INTO workouts SELECT * FROM restored.workouts")
                    sqlite.execSQL("INSERT INTO workout_exercises SELECT * FROM restored.workout_exercises")
                    sqlite.execSQL("INSERT INTO exercise_sets SELECT * FROM restored.exercise_sets")
                    sqlite.execSQL("INSERT INTO workout_name_notes SELECT * FROM restored.workout_name_notes")
                }
            } finally {
                sqlite.execSQL("DETACH DATABASE restored")
            }
            database.invalidationTracker.refreshAsync()
        }
    }

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
