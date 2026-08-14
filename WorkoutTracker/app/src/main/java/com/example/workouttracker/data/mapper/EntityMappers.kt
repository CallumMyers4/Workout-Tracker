package com.example.workouttracker.data.mapper

import com.example.workouttracker.core.model.CatalogExercise
import com.example.workouttracker.core.model.ExerciseSet
import com.example.workouttracker.core.model.Workout
import com.example.workouttracker.core.model.WorkoutExercise
import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.data.local.entity.CatalogExerciseEntity
import com.example.workouttracker.data.local.entity.ExerciseSetEntity
import com.example.workouttracker.data.local.entity.WorkoutEntity
import com.example.workouttracker.data.local.entity.WorkoutExerciseEntity

// Convert a saved catalog exercise into the model used by the rest of the app
fun CatalogExerciseEntity.toDomain() = CatalogExercise(
    id = id,
    name = name,
    goalKg = goalKg,
    note = note,
)

// Convert a catalog exercise model into a database row
fun CatalogExercise.toEntity() = CatalogExerciseEntity(
    id = id,
    name = name.trim(),
    goalKg = goalKg,
    note = note,
)

// Convert a saved set into the model used by the rest of the app
fun ExerciseSetEntity.toDomain() = ExerciseSet(
    id = id,
    position = position,
    reps = reps,
    weightKg = weightKg,
)

// Convert a set model into a database row owned by an exercise
fun ExerciseSet.toEntity(workoutExerciseId: Long) = ExerciseSetEntity(
    id = id,
    workoutExerciseId = workoutExerciseId,
    position = position,
    reps = reps,
    weightKg = weightKg,
)

// Combine a saved workout exercise and its sets into one model
fun WorkoutExerciseEntity.toDomain(
    catalogName: String,
    sets: List<ExerciseSetEntity>,
) = WorkoutExercise(
    id = id,
    catalogExerciseId = catalogExerciseId,
    name = catalogName,
    position = position,
    sets = sets.sortedBy { it.position }.map { it.toDomain() },
)

// Convert a workout exercise model into a database row owned by a workout
fun WorkoutExercise.toEntity(workoutId: Long) = WorkoutExerciseEntity(
    id = id,
    workoutId = workoutId,
    catalogExerciseId = catalogExerciseId,
    position = position,
)

// Combine a saved workout and its exercises into one model
fun WorkoutEntity.toDomain(exercises: List<WorkoutExercise>) = Workout(
    id = id,
    name = name,
    date = date,
    exercises = exercises.sortedBy { it.position },
)

// Convert a workout model into a database row
fun Workout.toEntity() = WorkoutEntity(id = id, name = name.trim(), date = date)

// Create the smaller workout model displayed on the home page
fun WorkoutEntity.toSummary(exercises: List<WorkoutExercise>) = WorkoutSummary(
    id = id,
    name = name,
    date = date,
    exerciseCount = exercises.map { it.catalogExerciseId }.distinct().size,
    exerciseNames = exercises.sortedBy { it.position }.map { it.name }.distinct(),
)
