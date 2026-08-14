package com.example.workouttracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.workouttracker.data.local.dao.ExerciseDao
import com.example.workouttracker.data.local.dao.SetDao
import com.example.workouttracker.data.local.dao.WorkoutDao
import com.example.workouttracker.data.local.entity.CatalogExerciseEntity
import com.example.workouttracker.data.local.entity.ExerciseSetEntity
import com.example.workouttracker.data.local.entity.WorkoutEntity
import com.example.workouttracker.data.local.entity.WorkoutExerciseEntity
import com.example.workouttracker.data.local.entity.WorkoutNameNoteEntity
import java.time.LocalDate

// Define the Room database tables, data access objects, and current schema version
@Database(
    entities = [
        WorkoutEntity::class,
        CatalogExerciseEntity::class,
        WorkoutExerciseEntity::class,
        ExerciseSetEntity::class,
        WorkoutNameNoteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(LocalDateConverters::class)
abstract class WorkoutDatabase : RoomDatabase() {
    // Return the queries which manage workouts
    abstract fun workoutDao(): WorkoutDao

    // Return the queries which manage exercises and shared notes
    abstract fun exerciseDao(): ExerciseDao

    // Return the queries which manage exercise sets
    abstract fun setDao(): SetDao

    companion object {
        const val DATABASE_NAME = "workout-tracker.db"

        @Volatile
        private var instance: WorkoutDatabase? = null

        // Return the one database instance used by the whole app
        fun create(context: Context): WorkoutDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2)
                    // Require migrations so a schema update never deletes workout history
                    .build()
                    .also { instance = it }
            }
        }
    }
}

// Convert LocalDate values to and from the text stored by SQLite
class LocalDateConverters {
    @TypeConverter
    // Read an ISO date stored in SQLite
    fun fromIsoDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    // Save a date as ISO text
    fun toIsoDate(value: LocalDate?): String? = value?.toString()
}

// Convert version 1 epoch-day dates into version 2 ISO date text
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recreate child tables so their foreign keys reference the replacement parent tables
        db.execSQL(
            """CREATE TABLE workouts_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                date TEXT NOT NULL
            )""",
        )
        db.execSQL(
            """INSERT INTO workouts_new (id, name, date)
                SELECT id, name, date('1970-01-01', CAST(date AS TEXT) || ' days') FROM workouts""",
        )
        db.execSQL(
            """CREATE TABLE workout_exercises_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutId INTEGER NOT NULL,
                catalogExerciseId INTEGER NOT NULL,
                position INTEGER NOT NULL,
                FOREIGN KEY(workoutId) REFERENCES workouts_new(id) ON DELETE CASCADE,
                FOREIGN KEY(catalogExerciseId) REFERENCES catalog_exercises(id) ON DELETE RESTRICT
            )""",
        )
        db.execSQL(
            """INSERT INTO workout_exercises_new
                SELECT id, workoutId, catalogExerciseId, position FROM workout_exercises""",
        )
        db.execSQL(
            """CREATE TABLE exercise_sets_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                workoutExerciseId INTEGER NOT NULL,
                position INTEGER NOT NULL,
                reps INTEGER NOT NULL,
                weightKg REAL NOT NULL,
                FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises_new(id) ON DELETE CASCADE
            )""",
        )
        db.execSQL(
            """INSERT INTO exercise_sets_new
                SELECT id, workoutExerciseId, position, reps, weightKg FROM exercise_sets""",
        )
        db.execSQL("DROP TABLE exercise_sets")
        db.execSQL("DROP TABLE workout_exercises")
        db.execSQL("DROP TABLE workouts")
        db.execSQL("ALTER TABLE workouts_new RENAME TO workouts")
        db.execSQL("ALTER TABLE workout_exercises_new RENAME TO workout_exercises")
        db.execSQL("ALTER TABLE exercise_sets_new RENAME TO exercise_sets")
        db.execSQL("CREATE INDEX index_workouts_date ON workouts(date)")
        db.execSQL("CREATE INDEX index_workouts_name ON workouts(name)")
        db.execSQL(
            "CREATE UNIQUE INDEX index_workout_exercises_workoutId_position " +
                "ON workout_exercises(workoutId, position)",
        )
        db.execSQL(
            "CREATE INDEX index_workout_exercises_catalogExerciseId " +
                "ON workout_exercises(catalogExerciseId)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX index_exercise_sets_workoutExerciseId_position " +
                "ON exercise_sets(workoutExerciseId, position)",
        )
    }
}
