package com.example.workouttracker.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.R
import com.example.workouttracker.domain.repository.BackupConnectionState
import com.example.workouttracker.ui.theme.ActionButton
import com.example.workouttracker.ui.theme.DestructiveButton
import com.example.workouttracker.ui.theme.GenericCard
import com.example.workouttracker.ui.theme.PageTitle

// Function to display the settings screen
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeChanged: (Boolean) -> Unit,
    onManageExercises: () -> Unit,
    onSignInOrOut: () -> Unit,
    onRequestBackup: () -> Unit,
    onRequestRestore: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmBackup: () -> Unit,
    onConfirmRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Keep the settings cards readable on tablets and landscape screens
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PageTitle(
                text = "Settings",
                icon = painterResource(R.drawable.icon_settings),
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                uiState.feedbackMessage?.let { message ->
                    MessageCard(message = message, isError = false)
                }
                uiState.errorMessage?.let { message ->
                    MessageCard(message = message, isError = true)
                }

                SettingsSectionCard(
                    title = "Appearance",
                    supportingText = "Choose how the app looks on this device.",
                ) {
                    ThemeSettingRow(
                        checked = uiState.preferences.darkTheme,
                        onCheckedChange = onThemeChanged,
                    )
                }

                SettingsSectionCard(
                    title = "Exercise library",
                    supportingText = "Add, rename, or combine exercises used in your workouts.",
                ) {
                    ActionButton(
                        text = "Manage exercise library",
                        onClick = onManageExercises,
                        onCard = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                DriveSettingsCard(
                    state = uiState.backupState,
                    onSignInOrOut = onSignInOrOut,
                    onRequestBackup = onRequestBackup,
                    onRequestRestore = onRequestRestore,
                )

                // Keep the final card clear of the bottom navigation
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Ask for confirmation before replacing the existing Drive backup
    if (uiState.showBackupConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text("Back up workouts?") },
            text = { Text("The latest Drive backup will be replaced with the current local data.") },
            confirmButton = { TextButton(onClick = onConfirmBackup) { Text("Back up") } },
            dismissButton = { TextButton(onClick = onDismissConfirmation) { Text("Cancel") } },
        )
    }

    // Ask for confirmation before overwriting all local data from Drive
    if (uiState.showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissConfirmation,
            title = { Text("Restore backup?") },
            text = { Text("This overwrites every workout, exercise, goal, and note currently stored on this device.") },
            confirmButton = { TextButton(onClick = onConfirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = onDismissConfirmation) { Text("Cancel") } },
        )
    }
}

// Create a consistently styled card for each group of settings
@Composable
private fun SettingsSectionCard(
    title: String,
    supportingText: String,
    content: @Composable () -> Unit,
) {
    GenericCard(
        title = title,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

// Display the dark theme setting as a clickable row
@Composable
private fun ThemeSettingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange)
            .semantics { role = Role.Switch }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Dark Theme", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Use darker colours throughout the app",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row handles clicks, so the switch does not need its own click handler
        Switch(checked = checked, onCheckedChange = null)
    }
}

// Display Google Drive connection, backup, and restore controls
@Composable
private fun DriveSettingsCard(
    state: BackupConnectionState,
    onSignInOrOut: () -> Unit,
    onRequestBackup: () -> Unit,
    onRequestRestore: () -> Unit,
) {
    // Disable connection controls while a Drive operation is running
    val busy = state.isBusy()
    val connected = state == BackupConnectionState.Connected
    val canChangeConnection = !busy && (
        connected || state == BackupConnectionState.SignedOut || state is BackupConnectionState.Error
        )

    SettingsSectionCard(
        title = "Google Drive Backup",
        supportingText = "Keep an optional copy of your complete workout database in Drive.",
    ) {
        //TODO: Make this a flash notification not constant, and user-friendly errors
        BackupStatus(state)
        if (state is BackupConnectionState.Error) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Show either connected or disconnected button based on state
        // Only show options to back up or restore when connected
        if (connected) {
                DestructiveButton(
                    text = "Disconnect Google Drive",
                    onClick = onSignInOrOut,
                    enabled = canChangeConnection,
                    onCard = true,
                    modifier = Modifier.fillMaxWidth()
                )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionButton(
                    text = "Back Up",
                    onClick = onRequestBackup,
                    onCard = true,
                    modifier = Modifier.weight(1f),
                )

                ActionButton(
                    text = "Restore",
                    onClick = onRequestBackup,
                    onCard = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        else
            ActionButton(
                text = "Connect Google Drive",
                onClick = onSignInOrOut,
                enabled = canChangeConnection,
                onCard = true,
                modifier = Modifier.fillMaxWidth()
            )
    }
}

// Display the current Drive status and a progress indicator for active operations
@Composable
private fun BackupStatus(state: BackupConnectionState) {
    val containerColor = when (state) {
        BackupConnectionState.Connected -> MaterialTheme.colorScheme.tertiaryContainer
        is BackupConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
        BackupConnectionState.Authorizing,
        BackupConnectionState.Uploading,
        BackupConnectionState.Restoring,
        -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (state) {
        BackupConnectionState.Connected -> MaterialTheme.colorScheme.onTertiaryContainer
        is BackupConnectionState.Error -> MaterialTheme.colorScheme.onErrorContainer
        BackupConnectionState.Authorizing,
        BackupConnectionState.Uploading,
        BackupConnectionState.Restoring,
        -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isBusy()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            }
            Text(
                text = state.statusLabel(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// Display a success or error message using the correct theme colours
@Composable
private fun MessageCard(message: String, isError: Boolean) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    GenericCard(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

// Return whether Google Drive is currently completing an operation
private fun BackupConnectionState.isBusy(): Boolean =
    this == BackupConnectionState.Authorizing ||
        this == BackupConnectionState.Uploading ||
        this == BackupConnectionState.Restoring

// Return a user-friendly label for each Google Drive state
private fun BackupConnectionState.statusLabel(): String = when (this) {
    BackupConnectionState.Unavailable -> "Google Play Services unavailable"
    BackupConnectionState.SignedOut -> "Not connected"
    BackupConnectionState.Authorizing -> "Connecting…"
    BackupConnectionState.Connected -> "Connected"
    BackupConnectionState.Uploading -> "Uploading backup…"
    BackupConnectionState.Restoring -> "Restoring backup…"
    is BackupConnectionState.Error -> "Connection error"
}
