package dev.milinko.movielens.ui.search

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.util.DevSettings
import dev.milinko.movielens.data.remote.vision.ScrapedMovie
import dev.milinko.movielens.data.remote.vision.TmdbMatch
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.auth.AuthViewModel
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.EmptyStateCard
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.PremiumBadge
import dev.milinko.movielens.ui.components.SectionHeader
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * One "secondary" TMDB match + the confidence of its parent resolution (confidence belongs to the
 * candidate/resolution, not to the individual TmdbMatch - see VisionModels.kt).
 */
internal data class SecondaryMatchEntry(val match: TmdbMatch, val confidencePercent: Int?)

/** All matches of all resolutions except the hero one, each with its resolution's confidence. */
internal fun buildSecondaryMatches(response: VisionSearchResponse, heroMatchId: Int?): List<SecondaryMatchEntry> {
    val result = mutableListOf<SecondaryMatchEntry>()
    response.resolutions.forEach { resolution ->
        val percent = resolution.candidate.confidence?.let { (it * 100).roundToInt() }
        resolution.matches.forEach { match ->
            if (match.id != heroMatchId) {
                result.add(SecondaryMatchEntry(match, percent))
            }
        }
    }
    return result
}

/**
 * Image search result in two levels: the best match is always expanded (`HeroMatchCard`); the other
 * matches are collapsed behind "Show N more" (`ExpandableMatchCard`), and each of those rows is
 * expandable itself.
 */
@Composable
internal fun VisionResultSection(
    response: VisionSearchResponse,
    onClear: () -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val firstResolution = response.resolutions.firstOrNull()
    val heroMatch = firstResolution?.matches?.firstOrNull()
    val heroConfidencePercent = firstResolution?.candidate?.confidence?.let { (it * 100).roundToInt() }
    val secondaryMatches = remember(response) { buildSecondaryMatches(response, heroMatch?.id) }
    var secondaryExpanded by remember(response) { mutableStateOf(false) }
    var expandedMatchIds by remember(response) { mutableStateOf(setOf<Int>()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.search_vision_result_title),
            actionText = stringResource(R.string.search_vision_clear),
            onActionClick = onClear
        )
        Text(
            text = if (response.notMovieLike) {
                stringResource(R.string.search_vision_not_movie_like)
            } else {
                stringResource(R.string.search_vision_success_message)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        if (heroMatch == null) {
            Text(stringResource(R.string.search_vision_no_hero), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                text = stringResource(R.string.search_vision_best_match_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            HeroMatchCard(
                match = heroMatch,
                confidencePercent = heroConfidencePercent,
                onClick = { onNavigateToDetail(heroMatch.id) }
            )

            if (secondaryMatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ExpandableMatchCard(
                    expanded = secondaryExpanded,
                    onToggleExpanded = { secondaryExpanded = !secondaryExpanded },
                    header = {
                        Text(
                            text = if (secondaryExpanded) {
                                stringResource(R.string.search_vision_hide_matches)
                            } else {
                                stringResource(R.string.search_vision_show_more_matches, secondaryMatches.size)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    expandedContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            secondaryMatches.forEach { entry ->
                                val isRowExpanded = entry.match.id in expandedMatchIds
                                SecondaryMatchRow(
                                    entry = entry,
                                    expanded = isRowExpanded,
                                    onToggle = {
                                        expandedMatchIds = if (isRowExpanded) {
                                            expandedMatchIds - entry.match.id
                                        } else {
                                            expandedMatchIds + entry.match.id
                                        }
                                    },
                                    onClick = { onNavigateToDetail(entry.match.id) }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Hero card of the best match: a larger poster, title, match percentage and (if it is in cinemas)
 * the first 1-2 cinemas directly visible as chips.
 */
@Composable
internal fun HeroMatchCard(match: TmdbMatch, confidencePercent: Int?, onClick: () -> Unit) {
    val poster = tmdbPosterUrl(match.posterPath, "w342")
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(130.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (poster != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(true).build(),
                        contentDescription = match.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = match.title, style = MaterialTheme.typography.titleMedium)
                if (confidencePercent != null) {
                    Text(
                        text = stringResource(R.string.search_vision_match_percent, confidencePercent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                match.releaseDate?.take(4)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (match.cinemaShowtimes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        match.cinemaShowtimes.take(2).forEach { show ->
                            GenreChip(text = show.cinemaName)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A secondary match row inside the collapsed list: small poster, title and %. If the movie is in
 * cinemas, the row expands to show its `ShowtimeCard`s instead of putting everything on screen.
 */
@Composable
internal fun SecondaryMatchRow(
    entry: SecondaryMatchEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val match = entry.match
    val poster = tmdbPosterUrl(match.posterPath, "w154")
    val hasShowtimes = match.cinemaShowtimes.isNotEmpty()

    ExpandableMatchCard(
        expanded = expanded,
        onToggleExpanded = onToggle,
        headerTogglesExpansion = false,
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(58.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (poster != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(true).build(),
                            contentDescription = match.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.confidencePercent != null) {
                        Text(
                            text = stringResource(R.string.search_vision_match_percent, entry.confidencePercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        expandedContent = if (hasShowtimes) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    match.cinemaShowtimes.forEach { show ->
                        ShowtimeCard(show = show)
                    }
                }
            }
        } else null
    )
}

/**
 * Cinema card inside the image search results - the same pattern as `CinemaShowtimeCard` on Movie
 * Detail, for `ScrapedMovie` instead of `Showtime` (two models with the same fields).
 */
@Composable
internal fun ShowtimeCard(show: ScrapedMovie) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = show.cinemaName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = show.cinemaLocation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, show.bookingUrl.toUri()))
                    }
                }) {
                    Text(text = stringResource(R.string.movie_detail_open_link), style = MaterialTheme.typography.labelLarge)
                }
                if (show.isUpcoming) {
                    GenreChip(text = stringResource(R.string.movie_detail_upcoming_badge))
                }
            }
        }
    }
}
