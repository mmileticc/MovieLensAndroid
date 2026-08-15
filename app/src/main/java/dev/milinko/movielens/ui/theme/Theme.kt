package dev.milinko.movielens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Faza 6 redizajna: MovieLens je NAMERNO uvek tamna app (Netflix-inspirisan identitet, videti
 * MOVIELENS_DESIGN_PLAN.md) - nema light temu, nema dynamicColor (ranije je bilo uključeno, pa je
 * app slučajno menjala boje prema korisnikovoj pozadini na Android 12+ telefonima umesto da ima
 * sopstveni identitet). Zato MovieLensTheme() više ne prima darkTheme/dynamicColor parametre -
 * jedini poziv (MainActivity.kt) ionako nije prosleđivao ništa osim content lambde.
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

@Composable
fun MovieLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MovieLensDarkColorScheme,
        typography = Typography,
        shapes = MovieLensShapes,
        content = content
    )
}
