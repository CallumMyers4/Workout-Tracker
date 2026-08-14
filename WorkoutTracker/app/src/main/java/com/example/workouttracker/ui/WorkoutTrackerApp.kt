package com.example.workouttracker.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.workouttracker.AppContainer
import com.example.workouttracker.domain.service.WorkoutValidator
import com.example.workouttracker.feature.goals.GoalsScreen
import com.example.workouttracker.feature.goals.GoalsViewModel
import com.example.workouttracker.feature.settings.ExerciseLibraryDialog
import com.example.workouttracker.feature.settings.SettingsScreen
import com.example.workouttracker.feature.settings.SettingsViewModel
import com.example.workouttracker.feature.workoutdetail.WorkoutDetailEvent
import com.example.workouttracker.feature.workoutdetail.WorkoutDetailScreen
import com.example.workouttracker.feature.workoutdetail.WorkoutDetailViewModel
import com.example.workouttracker.feature.workouteditor.WorkoutEditorEvent
import com.example.workouttracker.feature.workouteditor.WorkoutEditorScreen
import com.example.workouttracker.feature.workouteditor.WorkoutEditorViewModel
import com.example.workouttracker.feature.workoutlist.WorkoutListScreen
import com.example.workouttracker.feature.workoutlist.WorkoutListViewModel
import com.example.workouttracker.navigation.AppRoute

// Create the navigation graph and connect each page to its ViewModel
@Composable
fun WorkoutTrackerApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
    startDestination: AppRoute = AppRoute.WorkoutList,
) {
    val navController = rememberNavController()
    // Keep the selected workout ID so Home can return to its details page
    var activeWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { PrimaryNavigation(navController, activeWorkoutId) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            // Create the home workout list page
            composable<AppRoute.WorkoutList> {
                val model: WorkoutListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            WorkoutListViewModel(
                                container.workoutRepository,
                                container.preferencesRepository,
                            )
                        }
                    },
                )
                val state by model.uiState.collectAsStateWithLifecycle()
                WorkoutListScreen(
                    uiState = state,
                    onSearchChanged = model::onSearchChanged,
                    onFilterChanged = model::setFilter,
                    onSortChanged = model::setSort,
                    onGroupingChanged = model::setGrouping,
                    onGroupToggled = model::toggleGroup,
                    onWorkoutSelected = {
                        activeWorkoutId = it
                        navController.navigate(AppRoute.WorkoutDetail(it))
                    },
                    onLoadMore = model::loadMore,
                )
            }
            // Create the saved workout details page
            composable<AppRoute.WorkoutDetail> {
                val model: WorkoutDetailViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            WorkoutDetailViewModel(
                                createSavedStateHandle(),
                                container.workoutRepository,
                            )
                        }
                    },
                )
                val state by model.uiState.collectAsStateWithLifecycle()
                // Return to Home after the displayed workout is deleted
                LaunchedEffect(model) {
                    model.events.collect { event ->
                        if (event == WorkoutDetailEvent.Deleted) {
                            activeWorkoutId = null
                            navController.navigate(AppRoute.WorkoutList) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                WorkoutDetailScreen(
                    uiState = state,
                    onBack = {
                        activeWorkoutId = null
                        navController.popBackStack()
                    },
                    onEdit = { navController.navigate(AppRoute.WorkoutEditor(it)) },
                    onRequestDelete = model::requestDelete,
                    onCancelDelete = model::cancelDelete,
                    onConfirmDelete = model::confirmDelete,
                )
            }
            // Create the new or edit workout page
            composable<AppRoute.WorkoutEditor> {
                val model: WorkoutEditorViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            WorkoutEditorViewModel(
                                createSavedStateHandle(),
                                container.workoutRepository,
                                container.exerciseRepository,
                                WorkoutValidator(),
                            )
                        }
                    },
                )
                val state by model.uiState.collectAsStateWithLifecycle()
                // Open the workout details page after a successful save
                LaunchedEffect(model) {
                    model.events.collect { event ->
                        if (event is WorkoutEditorEvent.Saved) {
                            activeWorkoutId = event.workoutId
                            navController.navigate(AppRoute.WorkoutDetail(event.workoutId)) {
                                popUpTo<AppRoute.WorkoutEditor> { inclusive = true }
                            }
                        }
                    }
                }
                WorkoutEditorScreen(
                    uiState = state,
                    onNameChanged = model::updateWorkoutName,
                    onDateChanged = model::updateWorkoutDate,
                    onAddExercise = model::addExercise,
                    onRemoveExercise = model::removeExercise,
                    onExerciseSelected = model::selectExercise,
                    onExerciseCreated = model::createAndSelectExercise,
                    onOpenWorkoutNote = model::openWorkoutNote,
                    onOpenExerciseNote = model::openExerciseNote,
                    onNoteChanged = model::updateNote,
                    onSaveNote = model::saveNote,
                    onClearNote = model::clearNote,
                    onCloseNote = model::closeNote,
                    onExerciseToggled = model::toggleExerciseExpanded,
                    onAddSet = model::addSet,
                    onRemoveSet = model::removeSet,
                    onSetChanged = model::updateSet,
                    onRequestClear = model::requestClear,
                    onCancelClear = model::cancelClear,
                    onConfirmClear = model::confirmClear,
                    onSave = model::save,
                )
            }
            // Create the exercise goals page
            composable<AppRoute.Goals> {
                val model: GoalsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { GoalsViewModel(container.goalRepository) }
                    },
                )
                val state by model.uiState.collectAsStateWithLifecycle()
                GoalsScreen(
                    uiState = state,
                    onEditGoal = model::openGoalEditor,
                    onGoalInputChanged = model::updateGoalInput,
                    onSaveGoal = model::saveGoal,
                    onDismissGoalEditor = model::dismissGoalEditor,
                )
            }
            // Create the settings page and its exercise library dialog
            composable<AppRoute.Settings> {
                val model: SettingsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            SettingsViewModel(
                                container.preferencesRepository,
                                container.exerciseRepository,
                                container.backupRepository,
                            )
                        }
                    },
                )
                val state by model.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    uiState = state,
                    onThemeChanged = model::setDarkTheme,
                    onManageExercises = model::showExerciseLibrary,
                    onSignInOrOut = model::signInOrOut,
                    onRequestBackup = model::requestBackup,
                    onRequestRestore = model::requestRestore,
                    onDismissConfirmation = model::dismissConfirmation,
                    onConfirmBackup = model::confirmBackup,
                    onConfirmRestore = model::confirmRestore,
                )
                if (state.isExerciseLibraryVisible) {
                    ExerciseLibraryDialog(
                        exercises = state.exercises,
                        onAdd = model::addExercise,
                        onRename = model::renameExercise,
                        onDelete = model::deleteExercise,
                        onCombine = model::combineExercises,
                        onDismiss = model::hideExerciseLibrary,
                    )
                }
            }
        }
    }
}

// Display the main navigation bar and open the selected page
@Composable
private fun PrimaryNavigation(navController: NavHostController, activeWorkoutId: Long?) {
    // Hide navigation while typing to leave more room above the keyboard
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (keyboardVisible) return

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val items = listOf(
        "Home" to (activeWorkoutId?.let { AppRoute.WorkoutDetail(it) } ?: AppRoute.WorkoutList),
        "Log" to AppRoute.WorkoutEditor(),
        "Progress" to AppRoute.Goals,
        "Settings" to AppRoute.Settings,
    )
    NavigationBar(Modifier.fillMaxWidth()) {
        items.forEach { (label, route) ->
            NavigationBarItem(
                selected = destination?.hasRoute(route::class) == true,
                onClick = {
                    navController.navigate(route) {
                        // Preserve each tab's page state and unfinished workout values
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Text(label.take(1)) },
                label = { Text(label) },
            )
        }
    }
}
