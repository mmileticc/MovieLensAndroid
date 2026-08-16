package dev.milinko.movielens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Faza 6 redizajna: MovieLens NAMERNO nema dynamicColor (ranije je bilo uključeno, pa je app
 * slučajno menjala boje prema korisnikovoj pozadini na Android 12+ telefonima umesto da ima
 * sopstveni identitet) - to ostaje isključeno i dalje.
 *
 * Faza 2 (16. avgust 2026, MOVIELENS_DESIGN_PLAN.md): dodata prava light tema. "Tamna tema"
 * prekidač u Podešavanjima je ranije bio ožičen na backend /settings rutu koja nikad nije
 * postojala, pa nikad nije bio ni vidljiv - sad je čisto lokalno podešavanje
 * (`ThemePreferences`), a `MovieLensTheme` bira paletu na osnovu `darkTheme` parametra.
 */
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

@Composable
fun MovieLensTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) MovieLensDarkColorScheme else MovieLensLightColorScheme,
        typography = Typography,
        shapes = MovieLensShapes,
        content = content
    )
}
