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
 * Pager page 0: the regular text search (whole TMDB catalog) plus the image search result.
 */
@Composable
internal fun TextSearchResultsPage(
    uiState: SearchUiState,
    recentSearches: List<String>,
    onRecentQueryClick: (String) -> Unit,
    onRecentQueryRemove: (String) -> Unit,
    onClearAllRecent: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onClearVision: () -> Unit,
    onRecordHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // The image search result stays when the user starts typing a text search - both sections
        // can be on screen at once, stacked.
        uiState.visionResult?.let { response ->
            item {
                VisionResultSection(
                    response = response,
                    onClear = onClearVision,
                    // The query is added to the history only when the user actually opens a result
                    // (not on every keystroke).
                    onNavigateToDetail = { tmdbId ->
                        onRecordHistory()
                        onNavigateToDetail(tmdbId)
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (uiState.searchResults.isNotEmpty()) {
            if (uiState.visionResult != null) {
            // A small separate heading only when BOTH results are on screen at the same time.
                item {
                    SectionHeader(title = stringResource(R.string.search_text_results_header))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            items(uiState.searchResults, key = { it.tmdbId }) { movie ->
                MovieListRow(
                    title = movie.title,
                    posterPath = movie.posterPath,
                    metaText = movie.releaseDate?.take(4),
                    description = movie.overview,
                    ratingText = "${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}/10",
                    onClick = {
                        onRecordHistory()
                        onNavigateToDetail(movie.tmdbId)
                    }
                )
            }
        } else if (uiState.visionResult == null && uiState.error == null && uiState.searchQuery.isBlank()) {
            item {
                SearchEmptyState(
                    title = stringResource(R.string.search_empty_state_title),
                    message = stringResource(R.string.search_empty_state_message),
                    recentSearches = recentSearches,
                    onRecentQueryClick = onRecentQueryClick,
                    onRecentQueryRemove = onRecentQueryRemove,
                    onClearAllRecent = onClearAllRecent
                )
            }
        } else if (uiState.visionResult == null && uiState.error == null && !uiState.isSearching &&
            uiState.searchQuery.trim().length >= 2
        ) {
            // Distinguishes "no results" from "still loading" and "something failed".
            item {
                EmptyStateCard(
                    title = stringResource(R.string.search_no_results_title, uiState.searchQuery.trim()),
                    description = stringResource(R.string.search_no_results_message),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Pager page 1: semantic search limited to the movies currently in cinemas. No history and no image
 * section; the data source is `NowInCinemasMovie`.
 */
@Composable
internal fun CinemaResultsPage(
    uiState: SearchUiState,
    onNavigateToDetail: (Int) -> Unit,
    onRetryCatalog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (uiState.cinemaCatalogError != null && uiState.cinemaResults.isEmpty()) {
            // The programme is not loaded - the title filter has nothing to work on (otherwise this
            // would look like "No title matches").
            item {
                ErrorCard(
                    message = uiState.cinemaCatalogError,
                    onRetry = onRetryCatalog,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else if (uiState.cinemaResults.isNotEmpty()) {
            items(uiState.cinemaResults, key = { it.tmdbId }) { movie ->
                MovieListRow(
                    title = movie.title,
                    posterPath = movie.posterPath,
                    metaText = movie.cinemas.joinToString(", "),
                    ratingText = "${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}/10",
                    onClick = { onNavigateToDetail(movie.tmdbId) }
                )
            }
        } else if (uiState.error == null && uiState.searchQuery.isBlank()) {
            item {
                SearchEmptyState(
                    title = stringResource(R.string.search_cinemas_empty_title),
                    message = stringResource(R.string.search_cinemas_empty_message),
                    recentSearches = emptyList(),
                    onRecentQueryClick = {},
                    onRecentQueryRemove = {}
                )
            }
        } else if (uiState.error == null && !uiState.isSearchingCinemas && !uiState.isFilteringCinemasLive) {
            item {
                // Shown also when the live title filter (while typing) finds nothing, so it points
                // to the send button: the smarter (AI) search is still available.
                // !isFilteringCinemasLive prevents the message from flickering during the 300ms
                // debounce after each character, before the local filter finishes.
                Text(
                    text = stringResource(R.string.search_cinemas_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Empty state before the first search: recent searches as chips (tap to search again) above a
 * message with an animated icon. Without history (first visit) only the message is shown.
 */
@Composable
internal fun SearchEmptyState(
    // title/message are parameters so the same layout can be reused for the "In cinemas" mode
    // (which has no history - called with recentSearches = emptyList()).
    title: String,
    message: String,
    recentSearches: List<String>,
    onRecentQueryClick: (String) -> Unit,
    onRecentQueryRemove: (String) -> Unit,
    onClearAllRecent: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (recentSearches.isEmpty()) 48.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (recentSearches.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onClearAllRecent) {
                        Text(stringResource(R.string.search_recent_clear_all))
                    }
                }
                // A simple Column of Rows (2 per row) is enough for at most 8 items
                // (RecentSearchesStore.MAX_ITEMS).
                recentSearches.chunked(2).forEach { rowQueries ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowQueries.forEach { query ->
                            RecentSearchChip(
                                query = query,
                                onClick = { onRecentQueryClick(query) },
                                onRemove = { onRecentQueryRemove(query) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowQueries.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Animated icon badge above the message: like the logo circle on Sign In, but gently
        // "breathing" (a slow scale pulse).
        val infiniteTransition = rememberInfiniteTransition(label = "search_empty_pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "search_empty_pulse_scale"
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.27f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // The Movienee mark, same treatment as the badge on Sign In.
            Image(
                painter = painterResource(R.drawable.ic_movienee_mark),
                contentDescription = null,
                modifier = Modifier.size(42.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
