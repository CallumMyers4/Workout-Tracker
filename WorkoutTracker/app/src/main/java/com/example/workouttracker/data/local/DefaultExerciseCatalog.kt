package com.example.workouttracker.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.workouttracker.core.model.ExerciseType

// Exercises available immediately when a user opens a newly installed app
internal val DEFAULT_EXERCISES = listOf(
    DefaultExercise("Squat", ExerciseType.STRENGTH),
    DefaultExercise("Bench Press", ExerciseType.STRENGTH),
    DefaultExercise("Deadlift", ExerciseType.STRENGTH),
    DefaultExercise("Overhead Press", ExerciseType.STRENGTH),
    DefaultExercise("Barbell Row", ExerciseType.STRENGTH),
    DefaultExercise("Lat Pulldown", ExerciseType.STRENGTH),
    DefaultExercise("Leg Press", ExerciseType.STRENGTH),
    DefaultExercise("Bicep Curl", ExerciseType.STRENGTH),
    DefaultExercise("Tricep Extension", ExerciseType.STRENGTH),
    DefaultExercise("Running", ExerciseType.CARDIO),
    DefaultExercise("Walking", ExerciseType.CARDIO),
    DefaultExercise("Cycling", ExerciseType.CARDIO),
)

internal data class DefaultExercise(
    val name: String,
    val type: ExerciseType,
)

// Seed only databases created for new users; existing catalogues are left unchanged
internal val DEFAULT_EXERCISE_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        DEFAULT_EXERCISES.forEach { exercise ->
            db.execSQL(
                "INSERT OR IGNORE INTO catalog_exercises (name, type) VALUES (?, ?)",
                arrayOf(exercise.name, exercise.type.name),
            )
        }
    }
}
