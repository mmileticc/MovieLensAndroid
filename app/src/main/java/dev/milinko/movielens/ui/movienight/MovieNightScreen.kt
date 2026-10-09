package dev.milinko.movielens.ui.movienight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.EmptyStateCard
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.theme.MlPosterCornerRadius

/**
 * Movie Night: suggestions for watching together with a friend. The friend's name and the match
 * header (taste match, "You / Friend" for the main shared genre) appear ONCE at the top; shared
 * genres work as filter chips; each suggestion shows the year, TMDB rating and its own reason; the
 * list grows with infinite scroll and pull-to-refresh starts over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieNightScreen(
    viewModel: MovieNightViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = uiState.friendUsername?.let { stringResource(R.string.movie_night_title_with_friend, it) }
                        ?: stringResource(R.string.movie_night_top_bar_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when {
                    uiState.isLoading && uiState.totalLoaded == 0 -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    // Error and empty states are inside a scrollable column: PullToRefreshBox needs
                    // scrollable content, otherwise pull-to-refresh would not work in these states.
                    uiState.error != null -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            ErrorCard(
                                message = uiState.error ?: stringResource(R.string.movie_night_error_generic),
                                onRetry = viewModel::loadSuggestions
                            )
                        }
                    }
                    uiState.totalLoaded == 0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            EmptyStateCard(
                                title = stringResource(R.string.movie_night_empty_state),
                                actionText = stringResource(R.string.state_retry),
                                onAction = viewModel::refresh
                            )
                        }
                    }
                    else -> SuggestionsList(
                        uiState = uiState,
                        onGenreSelected = viewModel::onGenreSelected,
                        onLoadMore = viewModel::loadMore,
                        onNavigateToDetail = onNavigateToDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsList(
    uiState: MovieNightUiState,
    onGenreSelected: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    // Same infinite scroll pattern as HomeScreen/BrowseMovieList.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item { MatchHeader(uiState) }

        if (uiState.sharedGenres.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(uiState.sharedGenres, key = { it.id }) { genre ->
                        FilterChip(
                            selected = uiState.selectedGenreId == genre.id,
                            onClick = { onGenreSelected(genre.id) },
                            label = { Text(genre.name) }
                        )
                    }
                }
            }
        }

        if (uiState.suggestions.isEmpty()) {
            item {
                EmptyStateCard(
                    title = stringResource(R.string.browse_empty_filtered),
                    description = stringResource(R.string.movie_night_filter_empty_hint),
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(uiState.suggestions, key = { it.tmdbId }) { suggestion ->
                MovieSuggestionRow(suggestion = suggestion, onClick = { onNavigateToDetail(suggestion.tmdbId) })
            }
        }

        if (uiState.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Header: overall taste match + "You / Friend" for the main shared genre (once, not per row). */
@Composable
private fun MatchHeader(uiState: MovieNightUiState) {
    val primaryGenre = uiState.sharedGenres.firstOrNull()
    if (uiState.matchScore == null && primaryGenre == null) return

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            uiState.matchScore?.let { score ->
                Text(
                    text = stringResource(R.string.movie_night_match_score, score.toInt()),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (primaryGenre != null && uiState.yourMatch != null && uiState.theirMatch != null) {
                Text(
                    text = stringResource(R.string.movie_night_genre_affinity, primaryGenre.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                MatchBar(
                    label = stringResource(R.string.movie_night_your_match_label),
                    percent = uiState.yourMatch,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                MatchBar(
                    label = uiState.friendUsername ?: stringResource(R.string.movie_night_friend_match_label),
                    percent = uiState.theirMatch,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun MovieSuggestionRow(suggestion: MovieSuggestion, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(105.dp)
                    .clip(RoundedCornerShape(MlPosterCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (suggestion.posterPath != null) {
                    // AsyncImage needs fillMaxSize() BEFORE clip() inside a sized Box.
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(tmdbPosterUrl(suggestion.posterPath, "w185"))
                            .crossfade(true)
                            .build(),
                        contentDescription = suggestion.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(MlPosterCornerRadius))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                YearAndRating(suggestion)
                Text(
                    text = suggestion.matchReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** "★ 7.8 · 2024" - missing parts are skipped. */
@Composable
private fun YearAndRating(suggestion: MovieSuggestion) {
    val year = suggestion.releaseDate?.take(4)?.takeIf { it.isNotBlank() }
    val rating = suggestion.voteAverage?.takeIf { it > 0 }
        ?.let { String.format(LocalLocale.current.platformLocale, "%.1f", it) }
    if (year == null && rating == null) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        if (rating != null) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = listOfNotNull(rating, year).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Mini progress bar for one match percentage (You/Friend). */
@Composable
private fun MatchBar(label: String, percent: Float, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp)
        )
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(MaterialTheme.shapes.small),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${percent.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
