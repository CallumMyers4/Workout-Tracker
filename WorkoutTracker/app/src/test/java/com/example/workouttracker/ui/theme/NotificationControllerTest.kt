package com.example.workouttracker.ui.theme

import com.example.workouttracker.core.model.AppNotificationType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Check that the generic controller preserves notification content and display order
class NotificationControllerTest {
    @Test
    fun successAndErrorNotificationsAreQueuedInOrder() = runTest {
        val controller = NotificationController()

        controller.showSuccess("Saved.")
        controller.showError("Failed.")

        val success = controller.notifications.receive()
        val error = controller.notifications.receive()
        assertEquals("Saved.", success.message)
        assertEquals(AppNotificationType.SUCCESS, success.type)
        assertEquals("Failed.", error.message)
        assertEquals(AppNotificationType.ERROR, error.type)
    }
}
