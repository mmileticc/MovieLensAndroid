package dev.milinko.movielens.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dizajn sistem (Faza 6 redizajna, videti MOVIELENS_DESIGN_PLAN.md u korenu Diplomski foldera) -
 * tamna, filmska (Netflix-inspirisana) paleta. App je NAMERNO uvek taman (nema light temu, nema
 * dynamicColor) - to je deo brend identiteta, ne korisnička podešavanja. Vrednosti ovde su
 * source-of-truth, Theme.kt ih samo mapira u Material3 ColorScheme sloteve.
 */

// Pozadine (od najtamnije ka najsvetlijoj - "elevation" preko svetline boje, ne senki, jer se
// senke skoro ne vide na ovako tamnoj pozadini)
val MlBackground = Color(0xFF0A0A0A)
val MlSurfaceElevated1 = Color(0xFF161616)
val MlSurfaceElevated2 = Color(0xFF222222)

// Linije/razdvajanje
val MlBorder = Color(0x1FFFFFFF) // ~12% belo

// Akcenti - crvena je glavni CTA, zlatna NAMERNO drugačija boja (ocene + "premium" signal, ne
// meša se vizuelno sa akcijama)
val MlAccentPrimary = Color(0xFFE50914)
val MlAccentPrimaryPressed = Color(0xFFB20710)
val MlAccentGold = Color(0xFFF5C518)

// Tekst (hijerarhija: primarni -> sekundarni -> muted)
val MlTextPrimary = Color(0xFFF5F5F5)
val MlTextSecondary = Color(0xFFA3A3A3)
val MlTextMuted = Color(0xFF6B6B6B)

// Status
val MlSuccess = Color(0xFF3DDC84)

// --- Light tema (Faza 2, MOVIELENS_DESIGN_PLAN.md) - iste akcentne boje kao tamna (brend
// identitet ostaje prepoznatljiv), samo se pozadine/tekst/linije okreću na svetlu varijantu.
val MlLightBackground = Color(0xFFF7F7F7)
val MlLightSurfaceElevated1 = Color(0xFFFFFFFF)
val MlLightSurfaceElevated2 = Color(0xFFEFEFEF)

val MlLightBorder = Color(0x1F000000) // ~12% crno

val MlLightTextPrimary = Color(0xFF1A1A1A)
val MlLightTextSecondary = Color(0xFF595959)
val MlLightTextMuted = Color(0xFF8C8C8C)

// --- Stari template ostaje ovde neiskorišćen (dead code, namerno NE brišemo po projektnoj
// konvenciji "nikad ne brišemo fajlove/kod bez razloga") - ali ništa iz Theme.kt više ne
// referencira ove vrednosti posle redizajna.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
