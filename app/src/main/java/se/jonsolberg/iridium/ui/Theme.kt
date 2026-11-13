package se.jonsolberg.iridium.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SyncGreen = Color(0xFF4CC26A)
val SyncAmber = Color(0xFFE8B23A)

private val scheme = darkColorScheme(
    primary = Color(0xFFD9CCAE),
    onPrimary = Color(0xFF1B1A17),
    primaryContainer = Color(0xFF3B3A35),
    onPrimaryContainer = Color(0xFFF1E8D3),
    secondary = Color(0xFF7FB0FF),
    secondaryContainer = Color(0xFF2B3A52),
    onSecondaryContainer = Color(0xFFDCE7FF),
    background = Color(0xFF121315),
    onBackground = Color(0xFFE6E3DD),
    surface = Color(0xFF17181B),
    onSurface = Color(0xFFE6E3DD),
    surfaceVariant = Color(0xFF24262A),
    onSurfaceVariant = Color(0xFFB9B5AD),
    surfaceContainer = Color(0xFF1C1D20),
    surfaceContainerHigh = Color(0xFF232428),
    surfaceContainerHighest = Color(0xFF2A2B30),
    outline = Color(0xFF55575C),
    error = Color(0xFFFF6B5E),
)

@Composable
fun IridiumTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
