package dev.milinko.movielens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.theme.MlPosterCornerRadius

/**
 * Compact vertical movie card (poster + title + optional caption) for horizontal rows (Home, Movie
 * Night).
 *
 * Takes plain String parameters on purpose: the app has several movie models (MovieSearchResult,
 * NowInCinemasMovie, WatchlistItem...) that share only title/posterPath/tmdbId, so every screen maps
 * its own model.
 */
@Composable
fun MoviePosterCard(
    title: String,
    posterPath: String?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .height(150.dp)
                .width(110.dp)
                .clip(RoundedCornerShape(MlPosterCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (posterPath != null) {
                AsyncImage(
                    model = tmdbPosterUrl(posterPath, "w154"),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(MlPosterCornerRadius))
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
