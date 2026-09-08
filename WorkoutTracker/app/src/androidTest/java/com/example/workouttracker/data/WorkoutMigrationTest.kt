package com.example.workouttracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workouttracker.data.local.MIGRATION_2_3
import com.example.workouttracker.data.local.WorkoutDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkoutDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationTwoToThreePreservesStrengthData() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL("INSERT INTO catalog_exercises (id, name, goalKg, note) VALUES (1, 'Squat', 100.0, 'note')")
            execSQL("INSERT INTO workouts (id, name, date) VALUES (1, 'Leg day', '2026-09-08')")
            execSQL("INSERT INTO workout_exercises (id, workoutId, catalogExerciseId, position) VALUES (1, 1, 1, 0)")
            execSQL("INSERT INTO exercise_sets (id, workoutExerciseId, position, reps, weightKg) VALUES (1, 1, 0, 5, 80.0)")
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT type FROM workouts WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("STRENGTH", cursor.getString(0))
            }
            db.query("SELECT type, goalKg FROM catalog_exercises WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals("STRENGTH", cursor.getString(0))
                assertEquals(100.0, cursor.getDouble(1), 0.0)
            }
            db.query("SELECT reps, weightKg FROM exercise_sets WHERE id = 1").use { cursor ->
                cursor.moveToFirst()
                assertEquals(5, cursor.getInt(0))
                assertEquals(80.0, cursor.getDouble(1), 0.0)
            }
        }
    }

    private companion object { const val DATABASE_NAME = "cardio-migration-test" }
}
