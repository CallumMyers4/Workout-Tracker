package com.example.workouttracker.feature.workoutlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.domain.repository.PreferencesRepository
import com.example.workouttracker.domain.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
// Manage workout searching, filtering, sorting, grouping, and incremental loading
class WorkoutListViewModel(
    private val workoutRepository: WorkoutRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkoutListUiState())
    val uiState: StateFlow<WorkoutListUiState> = _uiState.asStateFlow()
    private var allWorkouts = emptyList<com.example.workouttracker.core.model.WorkoutSummary>()
    private var searchJob: Job? = null

    // Reload the workout list whenever a saved browsing preference changes
    init {
        viewModelScope.launch {
            preferencesRepository.preferences.flatMapLatest { preferences ->
                _uiState.update {
                    it.copy(
                        searchText = preferences.searchText,
                        filter = preferences.filter,
                        sort = preferences.sort,
                        grouping = preferences.grouping,
                    )
                }
                val matchingWorkouts = workoutRepository.observeWorkoutSummaries(
                    preferences.searchText,
                    preferences.filter,
                    preferences.sort,
                    preferences.grouping,
                )
                val allSavedWorkouts = workoutRepository.observeWorkoutSummaries(
                    query = "",
                    filter = WorkoutFilter.ALL_TIME,
                    sort = preferences.sort,
                    grouping = preferences.grouping,
                )
                combine(matchingWorkouts, allSavedWorkouts) { matching, all -> matching to all.isNotEmpty() }
            }.onStart { _uiState.update { it.copy(isLoading = true) } }
                .catch { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.userMessage()) }
                }
                .collect { (workouts, hasAnyWorkouts) ->
                    allWorkouts = workouts
                    _uiState.update { it.copy(hasAnyWorkouts = hasAnyWorkouts) }
                    publish()
                }
        }
    }

    // Update the search immediately and save it after the user pauses typing
    fun onSearchChanged(value: String) {
        _uiState.update { it.copy(searchText = value) }
        searchJob?.cancel()
        // Wait briefly so the repository is not reloaded after every individual key press
        searchJob = viewModelScope.launch {
            delay(300)
            resetPaging()
            preferencesRepository.update { it.copy(searchText = value.trim()) }
        }
    }

    // Update the selected date filter
    fun setFilter(filter: WorkoutFilter) {
        updateBrowsePreference { it.copy(filter = filter) }
    }

    // Update the selected sorting order
    fun setSort(sort: WorkoutSort) {
        updateBrowsePreference { it.copy(sort = sort) }
    }

    // Update the grouping method and expand all groups
    fun setGrouping(grouping: WorkoutGrouping) {
        _uiState.update { it.copy(collapsedGroups = emptySet()) }
        updateBrowsePreference(resetPaging = false) { it.copy(grouping = grouping) }
    }

    // Expand or collapse one workout group
    fun toggleGroup(groupName: String) {
        _uiState.update { state ->
            val changed = state.collapsedGroups.toMutableSet().apply {
                if (!add(groupName)) remove(groupName)
            }
            state.copy(collapsedGroups = changed)
        }
    }

    // Increase the visible date range or load another page of workouts without dates
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.loadedItemCount >= allWorkouts.size) return
        val previousCount = state.loadedItemCount
        // Load complete date ranges so workouts from the same boundary day remain together
        do {
            val current = _uiState.value
            val datedCount = allWorkouts.count { it.date != null }
            val visibleDatedCount = visibleWorkouts(current.loadedDateWindowDays, 0).count { it.date != null }
            _uiState.update {
                if (visibleDatedCount < datedCount) {
                    it.copy(loadedDateWindowDays = it.loadedDateWindowDays + DATE_WINDOW_DAYS)
                } else {
                    it.copy(loadedUnknownDateCount = it.loadedUnknownDateCount + UNKNOWN_DATE_PAGE_SIZE)
                }
            }
            publish()
        } while (_uiState.value.loadedItemCount == previousCount && previousCount < allWorkouts.size)
    }

    // Clear the currently displayed error
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Save a browsing preference and optionally restart incremental loading
    private fun updateBrowsePreference(
        resetPaging: Boolean = true,
        transform: (com.example.workouttracker.core.model.AppPreferences) -> com.example.workouttracker.core.model.AppPreferences,
    ) {
        if (resetPaging) resetPaging()
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            runCatching { preferencesRepository.update(transform) }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = error.userMessage()) }
                }
        }
    }

    // Convert the currently visible workouts into groups for the screen
    private fun publish() {
        // Group only the visible page while preserving the repository sorting order
        val state = _uiState.value
        val visible = visibleWorkouts(state.loadedDateWindowDays, state.loadedUnknownDateCount)
        val grouping = _uiState.value.grouping
        val sections = visible.groupBy { workout ->
            when (grouping) {
                WorkoutGrouping.NONE -> "all"
                WorkoutGrouping.MONTH -> workout.date?.let { "month:${it.year}-${it.monthValue}" } ?: "unknown"
                WorkoutGrouping.YEAR -> workout.date?.let { "year:${it.year}" } ?: "unknown"
                WorkoutGrouping.WORKOUT_NAME -> "name:${workout.name.lowercase(Locale.ROOT)}"
            }
        }.map { (key, items) ->
            val title = when (grouping) {
                WorkoutGrouping.NONE -> "All workouts"
                WorkoutGrouping.MONTH -> items.first().date?.format(MONTH_FORMAT) ?: "Unknown date"
                WorkoutGrouping.YEAR -> items.first().date?.year?.toString() ?: "Unknown date"
                WorkoutGrouping.WORKOUT_NAME -> items.first().name
            }
            WorkoutSection(key, title, items)
        }
        _uiState.update {
            it.copy(
                workouts = visible,
                sections = sections,
                loadedItemCount = visible.size,
                totalItemCount = allWorkouts.size,
                isLoading = false,
                errorMessage = null,
            )
        }
    }

    // Return workouts inside the loaded date range plus the loaded number of undated workouts
    private fun visibleWorkouts(dateWindowDays: Int, unknownDateCount: Int): List<com.example.workouttracker.core.model.WorkoutSummary> {
        val dated = allWorkouts.filter { it.date != null }
        val unknown = allWorkouts.filter { it.date == null }
        if (dated.isEmpty()) return unknown.take(unknownDateCount.coerceAtLeast(UNKNOWN_DATE_PAGE_SIZE))
        val anchor = requireNotNull(dated.first().date)
        val visibleDated = when (_uiState.value.sort) {
            WorkoutSort.NEWEST -> dated.filter { requireNotNull(it.date) >= anchor.minusDays((dateWindowDays - 1).toLong()) }
            WorkoutSort.OLDEST -> dated.filter { requireNotNull(it.date) <= anchor.plusDays((dateWindowDays - 1).toLong()) }
        }
        return if (visibleDated.size == dated.size) {
            visibleDated + unknown.take(unknownDateCount)
        } else {
            visibleDated
        }
    }

    // Return paging values to their starting position
    private fun resetPaging() {
        _uiState.update {
            it.copy(
                loadedItemCount = 0,
                loadedDateWindowDays = DATE_WINDOW_DAYS,
                loadedUnknownDateCount = 0,
            )
        }
    }

    // Give a user-friendly generic error
    private fun Throwable.userMessage(): String = message ?: "Something went wrong. Please try again."

    private companion object {
        const val DATE_WINDOW_DAYS = 14
        const val UNKNOWN_DATE_PAGE_SIZE = 20
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
