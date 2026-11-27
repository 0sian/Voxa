package dev.osianj.voxa.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Accent from the mock: used for the selected pill, play button and progress. Always paired with Ink text. */
val Lime = Color(0xFFC9EB4A)
val Ink = Color(0xFF1C1B17)

private val Paper = Color(0xFFEEE6D4)
private val PaperDeep = Color(0xFFE5DCC6)

private val LightScheme = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    primaryContainer = Lime, onPrimaryContainer = Ink,
    secondary = Ink, onSecondary = Paper,
    tertiary = Lime, onTertiary = Ink,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = PaperDeep, onSurfaceVariant = Color(0xFF6E6858),
    surfaceTint = Paper,
    outline = Ink, outlineVariant = Color(0xFFCBC2AB),
    error = Color(0xFFC23B22), onError = Color.White,
    surfaceContainerLowest = Paper, surfaceContainerLow = Paper, surfaceContainer = Paper,
    surfaceContainerHigh = Paper, surfaceContainerHighest = PaperDeep,
)

private val DarkPaper = Color(0xFF161512)
private val DarkPaperDeep = Color(0xFF22211C)
private val Cream = Color(0xFFEDE6D3)

private val DarkScheme = darkColorScheme(
    primary = Cream, onPrimary = DarkPaper,
    primaryContainer = Lime, onPrimaryContainer = Ink,
    secondary = Cream, onSecondary = DarkPaper,
    tertiary = Lime, onTertiary = Ink,
    background = DarkPaper, onBackground = Cream,
    surface = DarkPaper, onSurface = Cream,
    surfaceVariant = DarkPaperDeep, onSurfaceVariant = Color(0xFF9E9884),
    surfaceTint = DarkPaper,
    outline = Cream, outlineVariant = Color(0xFF3B392F),
    error = Color(0xFFFF6B55), onError = Ink,
    surfaceContainerLowest = DarkPaper, surfaceContainerLow = DarkPaper, surfaceContainer = DarkPaper,
    surfaceContainerHigh = DarkPaper, surfaceContainerHighest = DarkPaperDeep,
)

@Composable
fun VoxaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = VoxaTypography,
        content = content,
    )
}
