package dev.milinko.movielens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radius scale used across the app:
 * - small = chip, button, small badge
 * - medium = movie card, showtime card (the default for most cards)
 * - large = bottom sheet, dialog, bottom edge of the hero image
 */
val MovieLensShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp)
)

// Posters always use 8dp corners. Material 3 Shapes has no "poster" slot, so this is used directly.
val MlPosterCornerRadius = 8.dp
