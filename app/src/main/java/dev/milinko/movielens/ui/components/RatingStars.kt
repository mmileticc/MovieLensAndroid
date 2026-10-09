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
 * A row of 10 stars (1-10 scale, same as the backend); tapping star N sets rating N. Used on Movie
 * Detail and in the Library history tab. Filled stars use `colorScheme.secondary` (gold).
 */
@Composable
fun RatingStars(rating: Int, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    // For screen readers the whole row is one slider ("Rating, 7 of 10", adjusted by swiping up/down)
    // instead of 10 small buttons - ten 48dp touch targets would not fit the screen width.
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
            // The icon is smaller than the touch target (32dp Box) on purpose; otherwise 10 stars in
            // a row would be almost impossible to hit precisely (~22dp each without spacing).
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
