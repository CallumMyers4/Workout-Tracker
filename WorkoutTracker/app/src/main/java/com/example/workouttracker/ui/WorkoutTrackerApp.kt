package com.example.workouttracker.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.workouttracker.AppContainer
import com.example.workouttracker.R
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
import com.example.workouttracker.ui.theme.BottomNavigationButton

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
    // Keep an existing-workout edit as Home's active workspace while other tabs are used
    var editingWorkoutId by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = { PrimaryNavigation(navController, activeWorkoutId, editingWorkoutId) },
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
                        editingWorkoutId = null
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
                            editingWorkoutId = null
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
                        editingWorkoutId = null
                        navController.navigate(AppRoute.WorkoutList) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onEdit = {
                        editingWorkoutId = it
                        navController.navigate(AppRoute.EditWorkout(it))
                    },
                    onRequestDelete = model::requestDelete,
                    onCancelDelete = model::cancelDelete,
                    onConfirmDelete = model::confirmDelete,
                )
            }
            // Keep the Log tab as an independent new-workout workspace
            composable<AppRoute.WorkoutEditor> {
                WorkoutEditorDestination(
                    container = container,
                    isEditing = false,
                    onBack = { navController.popBackStack() },
                    onSaved = {},
                )
            }
            // Draw an existing-workout editor inside Home's retained navigation state
            composable<AppRoute.EditWorkout> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.EditWorkout>()
                LaunchedEffect(route.workoutId) {
                    activeWorkoutId = route.workoutId
                    editingWorkoutId = route.workoutId
                }
                val returnToDetails: (Long) -> Unit = { workoutId ->
                    activeWorkoutId = workoutId
                    editingWorkoutId = null
                    navController.navigate(AppRoute.WorkoutDetail(workoutId)) {
                        popUpTo<AppRoute.EditWorkout> { inclusive = true }
                        launchSingleTop = true
                    }
                }
                WorkoutEditorDestination(
                    container = container,
                    isEditing = true,
                    onBack = { returnToDetails(route.workoutId) },
                    onSaved = returnToDetails,
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

// Scope each create/edit destination to its own ViewModel and saved draft
@Composable
private fun WorkoutEditorDestination(
    container: AppContainer,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
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
    LaunchedEffect(model) {
        model.events.collect { event ->
            if (event is WorkoutEditorEvent.Saved) onSaved(event.workoutId)
        }
    }
    WorkoutEditorScreen(
        uiState = state,
        isEditing = isEditing,
        onBack = onBack,
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

// Create a class for each item detail
private data class NavigationItem(
    val tab: PrimaryTab,
    val label: String,
    val route: AppRoute,
    @param:DrawableRes val iconResource: Int,
)

private enum class PrimaryTab { HOME, LOG, PROGRESS, SETTINGS }

// Display the main navigation bar and open the selected page
@Composable
private fun PrimaryNavigation(
    navController: NavHostController,
    activeWorkoutId: Long?,
    editingWorkoutId: Long?,
) {
    // Hide navigation while typing to leave more room above the keyboard
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (keyboardVisible) return

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val items = listOf(
        NavigationItem(
            tab = PrimaryTab.HOME,
            label = "Home",
            route = editingWorkoutId?.let { AppRoute.EditWorkout(it) }
                ?: activeWorkoutId?.let { AppRoute.WorkoutDetail(it) }
                ?: AppRoute.WorkoutList,
            iconResource = R.drawable.icon_home,
        ),
        NavigationItem(
            PrimaryTab.LOG,
            "Log",
            AppRoute.WorkoutEditor,
            R.drawable.icon_add,
        ),
        NavigationItem(
            PrimaryTab.PROGRESS,
            "Progress",
            AppRoute.Goals,
            R.drawable.icon_progress,
        ),
        NavigationItem(
            PrimaryTab.SETTINGS,
            "Settings",
            AppRoute.Settings,
            R.drawable.icon_settings,
        ),
    )
    NavigationBar(Modifier.fillMaxWidth()) {
        items.forEach { item ->
            val selected = when (item.tab) {
                PrimaryTab.HOME -> destination?.hasRoute(AppRoute.WorkoutList::class) == true ||
                        destination?.hasRoute(AppRoute.WorkoutDetail::class) == true ||
                        destination?.hasRoute(AppRoute.EditWorkout::class) == true
                PrimaryTab.LOG -> destination?.hasRoute(AppRoute.WorkoutEditor::class) == true
                PrimaryTab.PROGRESS -> destination?.hasRoute(AppRoute.Goals::class) == true
                PrimaryTab.SETTINGS -> destination?.hasRoute(AppRoute.Settings::class) == true
            }
            BottomNavigationButton(
                label = item.label,
                icon = painterResource(item.iconResource),
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            // Preserve each tab's page state and unfinished workout values
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}
