package com.example.workouttracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Colors to use when in dark mode
private val DarkColorScheme = darkColorScheme(
    // Main actions like save/edit
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    // Softer primary highlight for selected controls or emphasized cards
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    // Supporting solid actions which should be quieter than Save or Edit
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    // Tonal button and selected navigation backgrounds in dark mode
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    // Accent for states such as selected nav bar and Drive connection status
    tertiary = DarkAccent,
    onTertiary = DarkOnAccent,
    // Softer accent background used by the connected Drive status card
    tertiaryContainer = DarkAccentContainer,
    onTertiaryContainer = DarkOnAccentContainer,
    // Validation errors, and error indicators
    error = DarkError,
    onError = DarkOnError,
    // Destructive action buttons
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    // Main page background behind Home, Log, Progress, and Settings
    background = DarkBackground,
    onBackground = DarkOnBackground,
    // Default dark surface for cards, dialogs, menus, and navigation
    surface = DarkBackground,
    onSurface = DarkOnBackground,
    // Neutral alternate surface for subdued cards and status areas
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    // Borders for outlined buttons, text fields, cards, and dividers
    outline = DarkOutline,
)

// Colors to use when in light mode
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightAccent,
    onTertiary = LightOnAccent,
    tertiaryContainer = LightAccentContainer,
    onTertiaryContainer = LightOnAccentContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
)

@Composable
// Apply the selected theme and use Android dynamic colours when available
fun WorkoutTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
