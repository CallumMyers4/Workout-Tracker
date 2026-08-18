package com.example.workouttracker.feature.workoutlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.example.workouttracker.R
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.WorkoutFilter
import com.example.workouttracker.core.model.WorkoutGrouping
import com.example.workouttracker.core.model.WorkoutSort
import com.example.workouttracker.ui.theme.GenericButton
import com.example.workouttracker.ui.theme.EmptyStateTextStyle
import com.example.workouttracker.ui.theme.PageTitle

// Function to display the searchable and grouped workout list
@Composable
fun WorkoutListScreen(
    uiState: WorkoutListUiState,
    onSearchChanged: (String) -> Unit,
    onFilterChanged: (WorkoutFilter) -> Unit,
    onSortChanged: (WorkoutSort) -> Unit,
    onGroupingChanged: (WorkoutGrouping) -> Unit,
    onGroupToggled: (String) -> Unit,
    onWorkoutSelected: (Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PageTitle(
            text = "Home",
            icon = painterResource(R.drawable.icon_home)
        )
        // Display the search input and dropdown browsing controls
        OutlinedTextField(
            value = uiState.searchText,
            onValueChange = onSearchChanged,
            label = { Text("Search workouts") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(
                bottom = 12.dp,
                start = 12.dp,
                end = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChoiceDropdown(
                title = "Range",
                values = WorkoutFilter.entries,
                selected = uiState.filter,
                label = { it.displayName() },
                onSelected = onFilterChanged,
                modifier = Modifier.weight(1f),
            )
            ChoiceDropdown(
                title = "Sort",
                values = WorkoutSort.entries,
                selected = uiState.sort,
                label = { it.displayName() },
                onSelected = onSortChanged,
                modifier = Modifier.weight(1f),
            )
            ChoiceDropdown(
                title = "Group",
                values = WorkoutGrouping.entries,
                selected = uiState.grouping,
                label = { it.displayName() },
                onSelected = onGroupingChanged,
                modifier = Modifier.weight(1f),
            )
        }
        if (uiState.totalItemCount > 0) {
            Text(
                "Showing ${uiState.loadedItemCount} of ${uiState.totalItemCount} workouts",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic
            )
        }

        when {
            // Display loading, error, or empty messages when no workout cards are available
            uiState.isLoading && uiState.workouts.isEmpty() -> CircularProgressIndicator(Modifier.padding(24.dp))
            uiState.errorMessage != null && uiState.workouts.isEmpty() -> Text(
                uiState.errorMessage,
                Modifier.padding(24.dp),
            )
            uiState.workouts.isEmpty() -> Text(
                if (uiState.hasAnyWorkouts) {
                    "No workouts match your current search and filters.\n" +
                    "Try changing the search, date, sort, or grouping controls."
                } else {
                    "No workouts found.\n" +
                    "Go to the Log page to record your first workout!"
                },
                Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                style = EmptyStateTextStyle,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Display each visible group and the workouts inside expanded groups
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.sections.forEach { section ->
                    item(key = "header:${section.key}") {
                        WorkoutGroupHeader(
                            title = section.title,
                            collapsed = section.key in uiState.collapsedGroups,
                            onToggle = { onGroupToggled(section.key) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (section.key !in uiState.collapsedGroups) {
                        items(section.workouts, key = { it.id }) { workout ->
                            WorkoutCard(
                                workout = workout,
                                onOpen = { onWorkoutSelected(workout.id) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
                if (uiState.loadedItemCount < uiState.totalItemCount) {
                    item(key = "load:${uiState.loadedItemCount}") {
                        // Automatically request the next page when this final row becomes visible
                        LaunchedEffect(uiState.loadedItemCount) { onLoadMore() }
                        CircularProgressIndicator(Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}

// Display a compact dropdown for one set of workout browsing choices
@Composable
private fun <T> ChoiceDropdown(
    title: String,
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        GenericButton(
            text = "$title: \n${label(selected)}",
            onClick = { expanded = !expanded },
            icon = painterResource(
                if (expanded) R.drawable.icon_collapse
                else R.drawable.icon_expand
            ),
            onCard = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(label(value)) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

// Convert stored enum names into labels suitable for the workout list
private fun Enum<*>.displayName(): String = when (this) {
    WorkoutFilter.RECENT_30_DAYS -> "30 days"
    WorkoutFilter.RECENT_90_DAYS -> "90 days"
    WorkoutGrouping.WORKOUT_NAME -> "Name"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}
