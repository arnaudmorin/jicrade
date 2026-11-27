package fr.amorin.jicrade.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E1F9),
    onSecondaryContainer = Color(0xFF181A2C),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FC),
    surfaceContainer = Color(0xFFF0EDF7),
    surfaceContainerHigh = Color(0xFFEAE7F1),
    surfaceContainerHighest = Color(0xFFE4E1EC),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC7C5D0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    onSecondary = Color(0xFF2D2F42),
    secondaryContainer = Color(0xFF434659),
    onSecondaryContainer = Color(0xFFE0E1F9),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF2A292F),
    surfaceContainerHighest = Color(0xFF35343A),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
)

private val priorityLight = mapOf(
    "P1" to (Color(0xFFFFDAD6) to Color(0xFF8C1D18)),
    "P2" to (Color(0xFFFFDCC2) to Color(0xFF6E3500)),
    "P3" to (Color(0xFFFFEFA8) to Color(0xFF574500)),
    "P4" to (Color(0xFFD8E2FF) to Color(0xFF0B3F8C)),
    "P5" to (Color(0xFFE4E1EC) to Color(0xFF46464F)),
)

private val priorityDark = mapOf(
    "P1" to (Color(0xFF93000A) to Color(0xFFFFDAD6)),
    "P2" to (Color(0xFF6E3500) to Color(0xFFFFDCC2)),
    "P3" to (Color(0xFF574500) to Color(0xFFFFEFA8)),
    "P4" to (Color(0xFF0B3F8C) to Color(0xFFD8E2FF)),
    "P5" to (Color(0xFF46464F) to Color(0xFFE4E1EC)),
)

@Composable
fun priorityColors(priority: String): Pair<Color, Color> {
    val colors = if (isSystemInDarkTheme()) priorityDark else priorityLight
    return colors[priority] ?: colors.getValue("P5")
}

val personColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF43A047),
    Color(0xFFE53935),
    Color(0xFF8E24AA),
    Color(0xFFFFB300),
    Color(0xFF00897B),
    Color(0xFF6D4C41),
    Color(0xFFD81B60),
    Color(0xFF3949AB),
    Color(0xFF00ACC1),
    Color(0xFF7CB342),
    Color(0xFF546E7A),
)

@Composable
fun JicradeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
