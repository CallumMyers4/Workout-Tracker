package com.example.workouttracker.core.model

// Describe a short-lived result message which can be displayed from any page
data class AppNotification(
    val message: String,
    val type: AppNotificationType,
)

// Select the visual treatment used for an action result
enum class AppNotificationType {
    SUCCESS,
    ERROR,
}
