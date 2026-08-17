package dev.milinko.movielens.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Dizajn sistem (Faza 6 redizajna, videti MOVIELENS_DESIGN_PLAN.md u korenu Diplomski foldera) -
 * tamna, filmska (Netflix-inspirisana) paleta kao podrazumevana. Faza 2 je dodala i pravu light
 * temu (ispod) - nema dynamicColor (namerno, brend identitet ostaje isti bez obzira na
 * korisnikovu pozadinu na Android 12+). Vrednosti ovde su source-of-truth, Theme.kt ih samo
 * mapira u Material3 ColorScheme sloteve.
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
// identitet ostaje prepoznatljiv, crvena/zlatna se ne diraju), ali neutrali VIŠE NISU čist
// sivo/beli Android podrazumevani ton (dorada 17. avgust 2026, korisnička žalba "difoltna
// bolnička android bela") - topao, blago krem/pergament ton umesto hladnog sivog, u istom duhu
// kao postojeći `MlAccentGold` (kino/premium karakter se oseti i u light temi, ne samo u tamnoj).
// Relativni odnos svetline ostaje isti kao pre (background=bazna svetla podloga, surfaceElevated1
// je NAJSVETLIJI ton - beličaste kartice "iskaču" sa podloge, surfaceElevated2 tamniji od
// background-a - "utonula" polja poput input okvira) - menja se samo TON, ne hijerarhija.
val MlLightBackground = Color(0xFFF5EFE4)
val MlLightSurfaceElevated1 = Color(0xFFFFFCF6)
val MlLightSurfaceElevated2 = Color(0xFFEAE0CE)

val MlLightBorder = Color(0x263A2A1C) // ~15% toplo tamno smeđa (umesto čisto crne)

val MlLightTextPrimary = Color(0xFF2C231A)
val MlLightTextSecondary = Color(0xFF6B5B47)
val MlLightTextMuted = Color(0xFF9C8B72)

// --- Stari template ostaje ovde neiskorišćen (dead code, namerno NE brišemo po projektnoj
// konvenciji "nikad ne brišemo fajlove/kod bez razloga") - ali ništa iz Theme.kt više ne
// referencira ove vrednosti posle redizajna.
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
