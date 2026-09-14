package com.example.workouttracker.feature.home

import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.core.model.WorkoutType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    private val today = LocalDate.of(2026, 9, 14)

    @Test
    fun dashboardCalculatesStreakWeekAndThirtyDaySplit() {
        val workouts = listOf(
            summary(1, today, WorkoutType.STRENGTH),
            summary(2, today.minusDays(1), WorkoutType.CARDIO),
            summary(3, today.minusDays(2), WorkoutType.STRENGTH),
            summary(4, today.minusDays(29), WorkoutType.CARDIO),
            summary(5, today.minusDays(30), WorkoutType.STRENGTH),
        )

        val state = HomeViewModel.calculateHomeState(workouts, today)

        assertEquals(3, state.streakDays)
        assertEquals(1, state.workoutsThisWeek)
        assertEquals(4, state.workoutsLast30Days)
        assertEquals(2, state.strengthWorkouts)
        assertEquals(2, state.cardioWorkouts)
        assertEquals(listOf(1L, 2L, 3L), state.recentWorkouts.map { it.id })
    }

    @Test
    fun streakRemainsActiveOnFirstRestDay() {
        val workouts = listOf(
            summary(1, today.minusDays(1), WorkoutType.STRENGTH),
            summary(2, today.minusDays(2), WorkoutType.STRENGTH),
        )

        assertEquals(2, HomeViewModel.calculateHomeState(workouts, today).streakDays)
    }

    private fun summary(id: Long, date: LocalDate, type: WorkoutType) = WorkoutSummary(
        id = id,
        name = "Workout $id",
        date = date,
        exerciseCount = 1,
        exerciseNames = emptyList(),
        type = type,
    )
}
