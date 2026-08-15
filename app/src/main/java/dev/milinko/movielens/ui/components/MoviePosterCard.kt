package dev.milinko.movielens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * Kompaktna vertikalna kartica filma (poster + naslov + opcioni natpis ispod) - za horizontalne
 * redove (Home "Predloženo za tebe"/"U bioskopima sada", Movie Night). Zamenjuje ranije privatne
 * HomeScreen.PosterCard i deo MovieNightScreen.MovieSuggestionRow (Faza 6 redizajna, videti
 * MOVIELENS_DESIGN_PLAN.md 1.4).
 *
 * NAMERNO ne zna ništa o MovieSearchResult/NowInCinemasMovie/WatchlistItem/itd. - u appu ne
 * postoji jedan zajednički "movie" model (šest različitih data klasa dele title/posterPath/tmdbId
 * i ništa više), pa komponenta prima gole String parametre - svaki ekran sam mapira svoj model.
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
                    modifier = Modifier.clip(RoundedCornerShape(MlPosterCornerRadius))
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
