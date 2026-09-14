package com.example.workouttracker.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.core.model.WorkoutSummary
import com.example.workouttracker.core.model.WorkoutType
import com.example.workouttracker.domain.repository.WorkoutRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val workoutRepository: WorkoutRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeDashboard()
    }

    private fun observeDashboard() {
        viewModelScope.launch {
            workoutRepository.observeWorkoutSummaries(
                query = "",
                filter = WorkoutFilter.ALL_TIME,
                sort = WorkoutSort.NEWEST,
                grouping = WorkoutGrouping.NONE,
            ).catch { error ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = error.message ?: "Unable to load workouts.")
                }
            }.collect { workouts ->
                _uiState.value = calculateHomeState(workouts, LocalDate.now(clock))
            }
        }
    }

    companion object {
        fun calculateHomeState(workouts: List<WorkoutSummary>, today: LocalDate): HomeUiState {
            val dated = workouts.filter { it.date != null }.sortedByDescending { it.date }
            val last30Start = today.minusDays(29)
            val last30 = dated.filter { it.date!! in last30Start..today }
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val workoutDates = dated.mapNotNull { it.date }.filter { it <= today }.toSet()

            // A streak remains active for the rest day immediately after the last workout.
            var cursor = if (today in workoutDates) today else today.minusDays(1)
            var streak = 0
            while (cursor in workoutDates) {
                streak++
                cursor = cursor.minusDays(1)
            }

            return HomeUiState(
                streakDays = streak,
                workoutsThisWeek = dated.count { it.date!! in weekStart..today },
                workoutsLast30Days = last30.size,
                strengthWorkouts = last30.count { it.type == WorkoutType.STRENGTH },
                cardioWorkouts = last30.count { it.type == WorkoutType.CARDIO },
                recentWorkouts = dated.take(3),
                isLoading = false,
            )
        }
    }
}
