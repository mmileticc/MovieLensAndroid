package dev.milinko.movielens.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design system colors: a dark, cinematic palette by default plus a warm light theme. No dynamic
 * color on purpose, so the brand looks the same regardless of the user's wallpaper (Android 12+).
 * These values are the source of truth; Theme.kt only maps them to Material 3 ColorScheme slots.
 */

// Backgrounds, darkest to lightest. Elevation is expressed through lightness instead of shadows,
// which are barely visible on such a dark background.
val MlBackground = Color(0xFF0A0A0A)
val MlSurfaceElevated1 = Color(0xFF161616)
val MlSurfaceElevated2 = Color(0xFF222222)

// Lines / dividers
val MlBorder = Color(0x1FFFFFFF) // ~12% white

// Accents: red is the main call to action; gold is deliberately different (ratings and the
// "premium" signal) so it never looks like an action.
val MlAccentPrimary = Color(0xFFE50914)
val MlAccentPrimaryPressed = Color(0xFFB20710)
val MlAccentGold = Color(0xFFF5C518)

// Text hierarchy: primary -> secondary -> muted
val MlTextPrimary = Color(0xFFF5F5F5)
val MlTextSecondary = Color(0xFFA3A3A3)
val MlTextMuted = Color(0xFF6B6B6B)

// Status
val MlSuccess = Color(0xFF3DDC84)

// --- Light theme: the same accent colors as the dark one (brand stays recognizable), with warm
// cream/parchment neutrals instead of the cold default Android grey/white. The lightness hierarchy
// matches the dark theme: surfaceElevated1 is the LIGHTEST tone (cards stand out from the
// background) and surfaceElevated2 is darker than the background ("sunken" fields like inputs).
val MlLightBackground = Color(0xFFF5EFE4)
val MlLightSurfaceElevated1 = Color(0xFFFFFCF6)
val MlLightSurfaceElevated2 = Color(0xFFEAE0CE)

val MlLightBorder = Color(0x263A2A1C) // ~15% warm dark brown (instead of pure black)

val MlLightTextPrimary = Color(0xFF2C231A)
val MlLightTextSecondary = Color(0xFF6B5B47)
val MlLightTextMuted = Color(0xFF9C8B72)

// --- Colors from the original project template; no longer referenced by Theme.kt.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
