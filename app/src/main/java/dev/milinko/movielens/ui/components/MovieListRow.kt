package dev.milinko.movielens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.theme.MlPosterCornerRadius

/**
 * Horizontalan red filma (poster + naslov + meta/opis/ocena + opcioni "trailing" slot). Zamenjuje
 * HomeScreen.MovieCard, SearchScreen.MovieSearchResultCard, LibraryScreen.WatchlistItemCard,
 * LibraryScreen.HistoryItemCard (bez inline izmene ocene - taj deo ostaje u LibraryScreen jer se
 * širi ISPOD reda, ne pored njega), MovieNightScreen.MovieSuggestionRow i
 * FriendProfileScreen.WatchedMovieRow (Faza 6, videti MOVIELENS_DESIGN_PLAN.md 1.4).
 *
 * `trailing` je namerno generički slot (IconButton za brisanje sa liste želja, tekst "Prioritet: N",
 * dugme za izmenu ocene...) - svaki ekran prosleđuje ono što mu treba.
 */
@Composable
fun MovieListRow(
    title: String,
    posterPath: String?,
    modifier: Modifier = Modifier,
    metaText: String? = null,
    description: String? = null,
    ratingText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(MlPosterCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (posterPath != null) {
                AsyncImage(
                    model = tmdbPosterUrl(posterPath, "w92"),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.clip(RoundedCornerShape(MlPosterCornerRadius))
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (metaText != null) {
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (ratingText != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.height(14.dp).width(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = ratingText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}
