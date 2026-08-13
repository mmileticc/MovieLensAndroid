package dev.milinko.movielens.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Red od 10 zvezdica (1-10 skala, ista kao backend) - tap na zvezdicu N postavlja ocenu N.
 * Zajednička komponenta (MovieDetailScreen + Library/Istorija tab) - videti MOVIELENS_ROADMAP.md,
 * ranije je postojala samo kao privatna komponenta na MovieDetailScreen.
 */
@Composable
fun RatingStars(rating: Int, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        for (i in 1..10) {
            // Ikonica je namerno manja od dodirnog cilja (Box 32dp) - 10 zvezdica u redu bi inače
            // bilo skoro nemoguće precizno pogoditi (svaka bi imala samo ~22dp bez razmaka).
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onRate(i) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Oceni sa $i",
                    tint = if (i <= rating) Color(0xFFF5C518) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
