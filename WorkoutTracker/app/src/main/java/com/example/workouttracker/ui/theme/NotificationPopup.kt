package com.example.workouttracker.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.AppNotification
import com.example.workouttracker.core.model.AppNotificationType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

// Positions supported by the generic action notification host
enum class NotificationPosition {
    TOP,
    CENTER,
    BOTTOM,
}

// Keep all editable notification appearance and timing values in one place
data class NotificationStyle(
    val successContainerColor: Color,
    val successContentColor: Color,
    val errorContainerColor: Color,
    val errorContentColor: Color,
    val position: NotificationPosition = NotificationPosition.BOTTOM,
    val durationMillis: Long = 3_000L,
    val horizontalPadding: Dp = 16.dp,
    val verticalPadding: Dp = 16.dp,
    val bottomNavigationOffset: Dp = 72.dp,
    val contentPadding: Dp = 16.dp,
    val maxWidth: Dp = 600.dp,
)

// Create theme-aware defaults which can later be replaced by saved user preferences
@Composable
fun defaultNotificationStyle(): NotificationStyle = NotificationStyle(
    successContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
    successContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    errorContainerColor = MaterialTheme.colorScheme.errorContainer,
    errorContentColor = MaterialTheme.colorScheme.onErrorContainer,
)

// Queue notifications so actions completed close together are still shown in order
class NotificationController internal constructor() {
    internal val notifications = Channel<AppNotification>(Channel.UNLIMITED)
    // Expose the active item so separate Compose dialog windows can mirror the popup
    var current by mutableStateOf<AppNotification?>(null)
        internal set

    // Add a completed action notification without blocking its ViewModel event collector
    fun show(notification: AppNotification) {
        notifications.trySend(notification)
    }

    // Provide convenient calls for pages which do not already hold a notification object
    fun showSuccess(message: String) = show(AppNotification(message, AppNotificationType.SUCCESS))
    fun showError(message: String) = show(AppNotification(message, AppNotificationType.ERROR))
}

// Retain one controller for the lifetime of the app UI
@Composable
fun rememberNotificationController(): NotificationController = remember { NotificationController() }

// Display queued notifications above every page without blocking interaction
@Composable
fun BoxScope.NotificationPopupHost(
    controller: NotificationController,
    modifier: Modifier = Modifier,
    style: NotificationStyle = defaultNotificationStyle(),
) {
    val currentDurationMillis by rememberUpdatedState(style.durationMillis)

    // Process the queue sequentially and apply the exact configured duration
    LaunchedEffect(controller) {
        for (notification in controller.notifications) {
            controller.current = notification
            delay(currentDurationMillis.coerceAtLeast(0L))
            controller.current = null
        }
    }

    NotificationPopupOverlay(
        notification = controller.current,
        modifier = modifier,
        style = style,
    )
}

// Render the current popup in either the app window or a separate dialog window
@Composable
fun BoxScope.NotificationPopupOverlay(
    notification: AppNotification?,
    modifier: Modifier = Modifier,
    style: NotificationStyle = defaultNotificationStyle(),
) {

    val alignment = when (style.position) {
        NotificationPosition.TOP -> Alignment.TopCenter
        NotificationPosition.CENTER -> Alignment.Center
        NotificationPosition.BOTTOM -> Alignment.BottomCenter
    }
    val bottomPadding = if (style.position == NotificationPosition.BOTTOM) {
        style.verticalPadding + style.bottomNavigationOffset
    } else {
        style.verticalPadding
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = style.horizontalPadding,
                top = style.verticalPadding,
                end = style.horizontalPadding,
                bottom = bottomPadding,
            ),
        contentAlignment = alignment,
    ) {
        AnimatedVisibility(
            visible = notification != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            notification?.let { currentNotification ->
                NotificationPopup(notification = currentNotification, style = style)
            }
        }
    }
}

// Draw one consistently styled success or failure popup
@Composable
private fun NotificationPopup(
    notification: AppNotification,
    style: NotificationStyle,
) {
    val isError = notification.type == AppNotificationType.ERROR
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) style.errorContainerColor else style.successContainerColor,
            contentColor = if (isError) style.errorContentColor else style.successContentColor,
        ),
        modifier = Modifier
            .widthIn(max = style.maxWidth)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = notification.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(style.contentPadding),
        )
    }
}
