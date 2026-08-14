package com.example.workouttracker.domain.service

import com.example.workouttracker.core.model.BestSet
import com.example.workouttracker.core.model.ExerciseSet

// Calculate an exercise's best set and its progress towards a goal
class ProgressCalculator {
    // Return the highest set for an exercise
    fun findBestSet(history: List<ExerciseSet>): BestSet? {
        // When weights tie, return the set with more reps
        val best = history.maxWithOrNull(
            compareBy<ExerciseSet> { it.weightKg }
                .thenBy { it.reps }
                .thenByDescending { it.position },
        ) ?: return null
        return BestSet(reps = best.reps, weightKg = best.weightKg)
    }

    // Return the current progress towards the goal of a given exercise if set
    fun calculatePercentage(bestSet: BestSet?, goalKg: Double?): Double? {
        if (bestSet == null || goalKg == null || !goalKg.isFinite() || goalKg <= 0.0) return null
        return bestSet.weightKg / goalKg * 100.0
    }
}
