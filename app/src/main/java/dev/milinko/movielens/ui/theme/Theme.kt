package dev.milinko.movielens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// No dynamicColor on purpose: the app keeps its own identity instead of following the wallpaper
// on Android 12+. The theme (dark/light) is a local setting (ThemePreferences).
private val MovieLensDarkColorScheme = darkColorScheme(
    primary = MlAccentPrimary,
    onPrimary = Color.White,
    primaryContainer = MlAccentPrimaryPressed,
    onPrimaryContainer = Color.White,

    secondary = MlAccentGold,
    onSecondary = Color(0xFF3A2C00),

    tertiary = MlSuccess,
    onTertiary = Color(0xFF06280F),

    background = MlBackground,
    onBackground = MlTextPrimary,

    surface = MlSurfaceElevated1,
    onSurface = MlTextPrimary,
    surfaceVariant = MlSurfaceElevated2,
    onSurfaceVariant = MlTextSecondary,

    outline = MlBorder,
    outlineVariant = MlBorder,

    error = Color(0xFFCF6679),
    onError = Color(0xFF3A0A0F),
)

private val MovieLensLightColorScheme = lightColorScheme(
    primary = MlAccentPrimary,
    onPrimary = Color.White,
    primaryContainer = MlAccentPrimaryPressed,
    onPrimaryContainer = Color.White,

    secondary = MlAccentGold,
    onSecondary = Color(0xFF3A2C00),

    tertiary = MlSuccess,
    onTertiary = Color(0xFF06280F),

    background = MlLightBackground,
    onBackground = MlLightTextPrimary,

    surface = MlLightSurfaceElevated1,
    onSurface = MlLightTextPrimary,
    surfaceVariant = MlLightSurfaceElevated2,
    onSurfaceVariant = MlLightTextSecondary,

    outline = MlLightBorder,
    outlineVariant = MlLightBorder,

    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** The app theme: picks the dark or light palette and applies the shared typography and shapes. */
@Composable
fun MovieLensTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) MovieLensDarkColorScheme else MovieLensLightColorScheme,
        typography = Typography,
        shapes = MovieLensShapes,
        content = content
    )
}
