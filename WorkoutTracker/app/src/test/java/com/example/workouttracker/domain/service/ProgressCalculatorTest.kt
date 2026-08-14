package com.example.workouttracker.domain.service

import com.example.workouttracker.core.model.ExerciseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Check best-set and goal percentage calculations
class ProgressCalculatorTest {
    private val calculator = ProgressCalculator()

    @Test
    // Confirm that repetitions come from the heaviest recorded set
    fun bestSetKeepsRepsFromHeaviestSet() {
        val best = calculator.findBestSet(
            listOf(
                ExerciseSet(position = 0, reps = 10, weightKg = 80.0),
                ExerciseSet(position = 1, reps = 4, weightKg = 100.0),
            ),
        )
        assertEquals(4, best?.reps)
        assertEquals(100.0, best?.weightKg ?: 0.0, 0.0)
    }

    @Test
    // Confirm that additional repetitions break a weight tie
    fun weightTiePrefersMoreReps() {
        val best = calculator.findBestSet(
            listOf(
                ExerciseSet(position = 0, reps = 3, weightKg = 100.0),
                ExerciseSet(position = 1, reps = 5, weightKg = 100.0),
            ),
        )
        assertEquals(5, best?.reps)
    }

    @Test
    // Confirm that percentage requires both history and a positive goal
    fun percentageRequiresHistoryAndPositiveGoal() {
        assertNull(calculator.calculatePercentage(null, 100.0))
        assertNull(calculator.calculatePercentage(calculator.findBestSet(emptyList()), 0.0))
    }
}
