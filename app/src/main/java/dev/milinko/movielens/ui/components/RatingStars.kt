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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.milinko.movielens.R
import kotlin.math.roundToInt

/**
 * Red od 10 zvezdica (1-10 skala, ista kao backend) - tap na zvezdicu N postavlja ocenu N.
 * Zajednička komponenta (MovieDetailScreen + Library/Istorija tab) - videti MOVIELENS_ROADMAP.md,
 * ranije je postojala samo kao privatna komponenta na MovieDetailScreen.
 *
 * Faza 6 redizajna: boja popunjene zvezdice je sad `MaterialTheme.colorScheme.secondary` umesto
 * hardkodovanog Color(0xFFF5C518) literala - u novoj temi je secondary = MlAccentGold (ista
 * vrednost), ali sad ide preko teme radi doslednosti/održivosti. contentDescription prebačen u
 * strings.xml (rating_stars_content_description) po novoj konvenciji lokalizacije.
 */
@Composable
fun RatingStars(rating: Int, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    // Za čitač ekrana ceo red je jedan klizač ("Ocena, 7 od 10", menja se prevlačenjem gore/dole)
    // umesto 10 malih dugmadi - 10 dodirnih ciljeva od 48dp ne bi stalo u širinu ekrana.
    val label = stringResource(R.string.rating_stars_label)
    val state = if (rating in 1..10) stringResource(R.string.rating_stars_state, rating) else stringResource(R.string.rating_stars_not_rated)
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = label
            stateDescription = state
            progressBarRangeInfo = ProgressBarRangeInfo(rating.coerceIn(0, 10).toFloat(), 0f..10f, steps = 9)
            setProgress { value ->
                val newRating = value.roundToInt().coerceIn(1, 10)
                if (newRating != rating) onRate(newRating)
                true
            }
        }
    ) {
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
                    contentDescription = stringResource(R.string.rating_stars_content_description, i),
                    tint = if (i <= rating) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
