package com.example.workouttracker.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.workouttracker.core.model.AppNotification
import com.example.workouttracker.core.model.AppNotificationType
import com.example.workouttracker.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

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
    val verticalPadding: Dp = 8.dp,
    val bottomNavigationOffset: Dp = 0.dp,
    val contentPadding: Dp = 16.dp,
    val maxWidth: Dp = 600.dp,
    val minimumHeight: Dp = 64.dp,
    val cornerRadius: Dp = 16.dp,
    val elevation: Dp = 10.dp,
    val statusIconSize: Dp = 22.dp,
    val statusIconBackgroundSize: Dp = 38.dp,
    val closeIconSize: Dp = 20.dp,
    val iconBackgroundColor: Color = Color.White,
    val successIconColor: Color = Color(0xFF08783E),
    val errorIconColor: Color = Color(0xFFC52832),
)

// Create theme-aware defaults which can later be replaced by saved user preferences
@Composable
fun defaultNotificationStyle(): NotificationStyle = NotificationStyle(
    successContainerColor = Color(0xFF08783E),
    successContentColor = Color.White,
    errorContainerColor = Color(0xFFC52832),
    errorContentColor = Color.White,
)

// Queue notifications so actions completed close together are still shown in order
class NotificationController internal constructor() {
    internal val notifications = Channel<AppNotification>(Channel.UNLIMITED)
    internal val dismissals = Channel<Unit>(Channel.CONFLATED)
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

    // Close the visible notification while allowing the next queued item to continue
    fun dismiss() {
        dismissals.trySend(Unit)
    }
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
            withTimeoutOrNull(currentDurationMillis.coerceAtLeast(0L)) {
                controller.dismissals.receive()
            }
            controller.current = null
        }
    }

    NotificationPopupOverlay(
        notification = controller.current,
        onDismiss = controller::dismiss,
        modifier = modifier,
        style = style,
    )
}

// Render the current popup in either the app window or a separate dialog window
@Composable
fun BoxScope.NotificationPopupOverlay(
    notification: AppNotification?,
    onDismiss: () -> Unit = {},
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
                NotificationPopup(
                    notification = currentNotification,
                    onDismiss = onDismiss,
                    style = style,
                )
            }
        }
    }
}

// Draw one consistently styled success or failure popup
@Composable
private fun NotificationPopup(
    notification: AppNotification,
    onDismiss: () -> Unit,
    style: NotificationStyle,
) {
    val isError = notification.type == AppNotificationType.ERROR
    val contentColor = if (isError) style.errorContentColor else style.successContentColor
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) style.errorContainerColor else style.successContainerColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(style.cornerRadius),
        elevation = CardDefaults.cardElevation(defaultElevation = style.elevation),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = style.maxWidth)
            .heightIn(min = style.minimumHeight)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = style.contentPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = style.iconBackgroundColor,
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(style.statusIconBackgroundSize),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(
                            if (isError) R.drawable.icon_notification_error
                            else R.drawable.icon_notification_success,
                        ),
                        contentDescription = null,
                        tint = if (isError) style.errorIconColor else style.successIconColor,
                        modifier = Modifier.size(style.statusIconSize),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.icon_close),
                    contentDescription = "Dismiss notification",
                    tint = contentColor,
                    modifier = Modifier.size(style.closeIconSize),
                )
            }
        }
    }
}
