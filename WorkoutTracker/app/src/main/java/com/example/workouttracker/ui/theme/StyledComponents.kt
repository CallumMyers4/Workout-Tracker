package com.example.workouttracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Title at the top of a page
@Composable
fun PageTitle(
    text: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    onIconClick: (() -> Unit)? = null,
    iconContentDescription: String? = null,
) {
    val titleStyle = MaterialTheme.typography.headlineLarge
    val titleColor = MaterialTheme.colorScheme.primary
    val iconSize = with(LocalDensity.current) { titleStyle.fontSize.toDp() }
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (isLightTheme) 0.72f else 0.35f
                )
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // Show the page icon on the left
        if (icon != null) {
            if (onIconClick == null) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = titleColor,
                )
            } else {
                IconButton(
                    onClick = onIconClick,
                    modifier = Modifier.size(iconSize),
                ) {
                    Icon(
                        painter = icon,
                        contentDescription = iconContentDescription,
                        modifier = Modifier.size(iconSize),
                        tint = titleColor,
                    )
                }
            }
        }
        // Show the title text
        Text(
            text = text,
            style = titleStyle,
            color = titleColor,
        )
    }
}

// Buttons which are just kinda there IDK (i.e. Add Exercise on Log)
@Composable
fun GenericButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onCard: Boolean = false,    // Use smaller text for buttons within cards
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = if (!onCard) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.displaySmall
                },
            )
            if (icon != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (onCard) 20.dp else 24.dp),
                )
            }
        }
    }
}

// Buttons with key actions (Save, Edit, Connect, etc.)
@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onCard: Boolean = false,    // Use smaller text for buttons within cards
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isLightTheme) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (isLightTheme) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = if (!onCard) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.displaySmall
                },
            )
        }
    }
}

// Buttons with irreversible actions (Delete and similar)
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onCard: Boolean = false,    // Use smaller text for buttons within cards
) {
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isLightTheme) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            contentColor = if (isLightTheme) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                style = if (!onCard) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.displaySmall
                },
            )
        }
    }
}

// One reusable item inside the bottom navigation bar
@Composable
fun RowScope.BottomNavigationButton(
    label: String,
    icon: Painter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(40.dp),
        icon = {
            Icon(
                painter = icon,
                contentDescription = label,
            )
        },
        label = {
            Text(label)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

// Cards which are used multiple times and don't really need highlighted
@Composable
fun GenericCard(
    modifier: Modifier = Modifier,
    title: String? = null,  // Title of the card (optional)
    onClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit,    // The main content of a card, condensed in a column
) {
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Add title if given
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            // Generate content underneath
            content()
        }
    }
    // Use the clickable Card overload only when the caller supplies an action
    if (onClick == null) {
        Card(modifier = modifier, colors = colors, content = cardContent)
    } else {
        Card(onClick = onClick, modifier = modifier, colors = colors, content = cardContent)
    }
}
