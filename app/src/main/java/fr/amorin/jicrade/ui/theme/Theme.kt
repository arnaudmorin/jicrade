package fr.amorin.jicrade.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(primary = Color(0xFFB3261E), secondary = Color(0xFF625B71))
private val Dark = darkColorScheme(primary = Color(0xFFF2B8B5), secondary = Color(0xFFCCC2DC))

private val priorityColors = mapOf(
    "P1" to Color(0xFFD32F2F),
    "P2" to Color(0xFFF57C00),
    "P3" to Color(0xFFFBC02D),
    "P4" to Color(0xFF1976D2),
    "P5" to Color(0xFF757575),
)

fun priorityColor(priority: String): Color = priorityColors[priority] ?: Color(0xFF757575)

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
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
