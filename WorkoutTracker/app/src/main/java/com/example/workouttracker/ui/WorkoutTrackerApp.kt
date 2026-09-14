package com.example.workouttracker.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.example.workouttracker.core.model.AppPreferences
import com.example.workouttracker.core.model.WeightsUnit
import com.example.workouttracker.domain.service.WorkoutValidator
import com.example.workouttracker.feature.goals.GoalsScreen
import com.example.workouttracker.feature.goals.GoalsViewModel
import com.example.workouttracker.feature.home.HomeScreen
import com.example.workouttracker.feature.home.HomeViewModel
import com.example.workouttracker.feature.settings.ExerciseLibraryDialog
import com.example.workouttracker.feature.settings.SettingsScreen
import com.example.workouttracker.feature.settings.SettingsViewModel
import com.example.workouttracker.feature.settings.SettingsEvent
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
import com.example.workouttracker.ui.theme.NotificationPopupHost
import com.example.workouttracker.ui.theme.rememberNotificationController

// Create the navigation graph and connect each page to its ViewModel
@Composable
fun WorkoutTrackerApp(
    container: AppContainer,
    modifier: Modifier = Modifier,
    startDestination: AppRoute = AppRoute.Home,
) {
    val navController = rememberNavController()
    val notificationController = rememberNotificationController()
    // Observe the selected unit once and share it with every weight-based screen
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferences(),
    )
    var pendingTabRoute by remember { mutableStateOf<AppRoute?>(null) }
    var workoutRefreshKey by remember { mutableLongStateOf(0L) }
    // Keep Settings operations alive when the user navigates to another page
    val settingsModel: SettingsViewModel = viewModel(
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
    // Handle Drive and exercise results independently from the Settings destination
    LaunchedEffect(settingsModel) {
        settingsModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Notify -> notificationController.show(event.notification)
                SettingsEvent.DataRestored -> workoutRefreshKey++
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            PrimaryNavigation(
                navController = navController,
                onEditExitRequested = { pendingTabRoute = it },
            )
        },
    ) { innerPadding ->
        // Use the Scaffold content boundary to position notifications above navigation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize(),
            ) {
            // Create the dashboard home page
            composable<AppRoute.Home> {
                val model: HomeViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { HomeViewModel(container.workoutRepository) }
                    },
                )
                val state by model.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    uiState = state,
                    onViewHistory = { navController.navigate(AppRoute.WorkoutList) },
                    onWorkoutSelected = {
                        navController.navigate(AppRoute.WorkoutDetail(it, returnToHome = true))
                    },
                )
            }
            // Create the searchable workout history page
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
                // Reload retained History data after Settings restores the database
                LaunchedEffect(workoutRefreshKey) {
                    if (workoutRefreshKey > 0L) model.refresh()
                }
                WorkoutListScreen(
                    uiState = state,
                    onSearchChanged = model::onSearchChanged,
                    onFilterChanged = model::setFilter,
                    onTypeFilterChanged = model::setTypeFilter,
                    onSortChanged = model::setSort,
                    onGroupingChanged = model::setGrouping,
                    onGroupToggled = model::toggleGroup,
                    onWorkoutSelected = {
                        navController.navigate(AppRoute.WorkoutDetail(it))
                    },
                    onLoadMore = model::loadMore,
                    weightsUnit = preferences.weightsUnit,
                )
            }
            // Create the saved workout details page
            composable<AppRoute.WorkoutDetail> {
                val route = it.toRoute<AppRoute.WorkoutDetail>()
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
                // Return to the dashboard or History after the displayed workout is deleted
                LaunchedEffect(model) {
                    model.events.collect { event ->
                        when (event) {
                            WorkoutDetailEvent.Deleted -> {
                                navController.navigate(if (route.returnToHome) AppRoute.Home else AppRoute.WorkoutList) {
                                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                                    launchSingleTop = true
                                }
                            }
                            is WorkoutDetailEvent.Notify -> notificationController.show(event.notification)
                        }
                    }
                }
                WorkoutDetailScreen(
                    uiState = state,
                    weightsUnit = preferences.weightsUnit,
                    onBack = {
                        navController.navigate(if (route.returnToHome) AppRoute.Home else AppRoute.WorkoutList) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    onEdit = {
                        navController.navigate(AppRoute.EditWorkout(it, route.returnToHome))
                    },
                    onRequestDelete = model::requestDelete,
                    onCancelDelete = model::cancelDelete,
                    onConfirmDelete = model::confirmDelete,
                )
            }
            // Keep the central add action as an independent new-workout workspace
            composable<AppRoute.WorkoutEditor> {
                WorkoutEditorDestination(
                    container = container,
                    weightsUnit = preferences.weightsUnit,
                    isEditing = false,
                    onBack = { navController.popBackStack() },
                    onSaved = {},
                    onNotification = notificationController::show,
                )
            }
            // Draw an existing-workout editor within its originating section
            composable<AppRoute.EditWorkout> { backStackEntry ->
                val route = backStackEntry.toRoute<AppRoute.EditWorkout>()
                val returnToDetails: (Long) -> Unit = { workoutId ->
                    navController.navigate(AppRoute.WorkoutDetail(workoutId, route.returnToHome)) {
                        popUpTo<AppRoute.EditWorkout> { inclusive = true }
                        launchSingleTop = true
                    }
                }
                WorkoutEditorDestination(
                    container = container,
                    weightsUnit = preferences.weightsUnit,
                    isEditing = true,
                    onBack = { returnToDetails(route.workoutId) },
                    onSaved = returnToDetails,
                    onNotification = notificationController::show,
                    tabExitRequested = pendingTabRoute != null,
                    onCancelTabExit = { pendingTabRoute = null },
                    onConfirmTabExit = {
                        pendingTabRoute?.let { destination ->
                            pendingTabRoute = null
                            navController.navigate(destination) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    // Discard the retained detail/editor stack so the tab opens at its root.
                                    saveState = false
                                }
                                launchSingleTop = true
                                // Only the central add action may restore an unfinished new workout.
                                restoreState = destination == AppRoute.WorkoutEditor
                            }
                        }
                    },
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
                // Keep an open goal editor in sync when the unit setting changes
                LaunchedEffect(preferences.weightsUnit) {
                    model.setWeightsUnit(preferences.weightsUnit)
                }
                GoalsScreen(
                    uiState = state,
                    weightsUnit = preferences.weightsUnit,
                    onEditGoal = model::openGoalEditor,
                    onGoalInputChanged = model::updateGoalInput,
                    onGoalMinutesChanged = model::updateGoalMinutes,
                    onGoalSecondsChanged = model::updateGoalSeconds,
                    onTypeSelected = model::selectType,
                    onSaveGoal = model::saveGoal,
                    onDismissGoalEditor = model::dismissGoalEditor,
                )
            }
            // Create the settings page and its exercise library dialog
            composable<AppRoute.Settings> {
                val state by settingsModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    uiState = state,
                    onThemeChanged = settingsModel::setDarkTheme,
                    onWeightsUnitChanged = settingsModel::setWeightsUnit,
                    onManageExercises = settingsModel::showExerciseLibrary,
                    onSignInOrOut = settingsModel::signInOrOut,
                    onRequestBackup = settingsModel::requestBackup,
                    onRequestRestore = settingsModel::requestRestore,
                    onDismissConfirmation = settingsModel::dismissConfirmation,
                    onConfirmBackup = settingsModel::confirmBackup,
                    onConfirmRestore = settingsModel::confirmRestore,
                )
                if (state.isExerciseLibraryVisible) {
                    ExerciseLibraryDialog(
                        exercises = state.exercises,
                        onAdd = settingsModel::addExercise,
                        onRename = settingsModel::renameExercise,
                        onDelete = settingsModel::deleteExercise,
                        onCombine = settingsModel::combineExercises,
                        onDismiss = settingsModel::hideExerciseLibrary,
                        notificationController = notificationController,
                    )
                }
            }
            }
            // Keep action results visible across navigation with a small gap above the bar
            NotificationPopupHost(controller = notificationController)
        }
    }
}

// Scope each create/edit destination to its own ViewModel and saved draft
@Composable
private fun WorkoutEditorDestination(
    container: AppContainer,
    weightsUnit: WeightsUnit,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onNotification: (com.example.workouttracker.core.model.AppNotification) -> Unit,
    tabExitRequested: Boolean = false,
    onCancelTabExit: () -> Unit = {},
    onConfirmTabExit: () -> Unit = {},
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
    // Convert an active workout draft when the unit setting changes
    LaunchedEffect(weightsUnit) {
        model.setWeightsUnit(weightsUnit)
    }
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is WorkoutEditorEvent.Saved -> onSaved(event.workoutId)
                is WorkoutEditorEvent.Notify -> onNotification(event.notification)
            }
        }
    }
    WorkoutEditorScreen(
        uiState = state,
        weightsUnit = weightsUnit,
        isEditing = isEditing,
        onBack = onBack,
        onTypeSelected = model::selectWorkoutType,
        onBackToChooser = model::requestBackToChooser,
        tabExitRequested = tabExitRequested,
        onCancelTabExit = onCancelTabExit,
        onConfirmTabExit = onConfirmTabExit,
        onNameChanged = model::updateWorkoutName,
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
        onCardioEntryChanged = model::updateCardioEntry,
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

private enum class PrimaryTab { HOME, HISTORY, PROGRESS, SETTINGS }

// Display the main navigation bar and open the selected page
@Composable
private fun PrimaryNavigation(
    navController: NavHostController,
    onEditExitRequested: (AppRoute) -> Unit,
) {
    // Hide navigation while typing to leave more room above the keyboard
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    if (keyboardVisible) return

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val detailReturnsHome = if (destination?.hasRoute(AppRoute.WorkoutDetail::class) == true) {
        backStackEntry?.toRoute<AppRoute.WorkoutDetail>()?.returnToHome == true
    } else false
    val editReturnsHome = if (destination?.hasRoute(AppRoute.EditWorkout::class) == true) {
        backStackEntry?.toRoute<AppRoute.EditWorkout>()?.returnToHome == true
    } else false
    val items = listOf(
        NavigationItem(
            tab = PrimaryTab.HOME,
            label = "Home",
            route = AppRoute.Home,
            iconResource = R.drawable.icon_home,
        ),
        NavigationItem(
            PrimaryTab.HISTORY,
            "History",
            AppRoute.WorkoutList,
            R.drawable.icon_history,
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
    val newWorkoutSelected = destination?.hasRoute(AppRoute.WorkoutEditor::class) == true
    // Reserve both the raised-button area and the system navigation inset. The opaque
    // Surface is deliberately separate from that raised area, making the overlap visible.
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .zIndex(1f),
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 12.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        if (index == 2) Spacer(Modifier.width(72.dp))
                        val selected = when (item.tab) {
                            PrimaryTab.HOME -> destination?.hasRoute(AppRoute.Home::class) == true || detailReturnsHome || editReturnsHome
                            PrimaryTab.HISTORY -> destination?.hasRoute(AppRoute.WorkoutList::class) == true ||
                                (destination?.hasRoute(AppRoute.WorkoutDetail::class) == true && !detailReturnsHome) ||
                                (destination?.hasRoute(AppRoute.EditWorkout::class) == true && !editReturnsHome)
                            PrimaryTab.PROGRESS -> destination?.hasRoute(AppRoute.Goals::class) == true
                            PrimaryTab.SETTINGS -> destination?.hasRoute(AppRoute.Settings::class) == true
                        }
                        BottomNavigationButton(
                            label = item.label,
                            icon = painterResource(item.iconResource),
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!selected) {
                                    if (destination?.hasRoute(AppRoute.EditWorkout::class) == true) {
                                        onEditExitRequested(item.route)
                                    } else navController.navigate(item.route) {
                                        // Retain an unfinished new-workout draft while visiting another tab.
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = destination?.hasRoute(AppRoute.WorkoutEditor::class) == true
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
            FloatingActionButton(
                onClick = {
                    if (!newWorkoutSelected) {
                        if (destination?.hasRoute(AppRoute.EditWorkout::class) == true) {
                            onEditExitRequested(AppRoute.WorkoutEditor)
                        } else navController.navigate(AppRoute.WorkoutEditor) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(64.dp)
                    .border(4.dp, MaterialTheme.colorScheme.background, androidx.compose.foundation.shape.CircleShape)
                    .zIndex(2f),
                shape = androidx.compose.foundation.shape.CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                    defaultElevation = 10.dp,
                    pressedElevation = 14.dp,
                ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.icon_add),
                    contentDescription = "New workout",
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}
