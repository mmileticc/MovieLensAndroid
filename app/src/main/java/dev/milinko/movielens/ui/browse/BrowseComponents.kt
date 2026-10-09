package dev.milinko.movielens.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.milinko.movielens.R
import dev.milinko.movielens.data.pref.BrowseViewMode
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.EmptyStateCard
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.theme.MlPosterCornerRadius

/**
 * Shared UI model for the "See all" screens (Suggested for you / In cinemas). The app has no single
 * movie model, so each screen maps its own (MovieSearchResult / NowInCinemasMovie) into this one and
 * the grid card and list row exist only once.
 */
data class BrowseMovie(
    val tmdbId: Int,
    val title: String,
    val posterPath: String?,
    val rating: Double,
    val year: String?,
    val runtimeMinutes: Int?,
    val genreNames: List<String>,
    val overview: String?,
    // E.g. "Cineplexx, Cine Grand +2" on the In cinemas screen; null on Suggested.
    val subtitle: String?
)

/** "Cineplexx, Cine Grand +2" - the first two cinemas, then the number of the rest. */
fun cinemasSubtitle(cinemas: List<String>): String? = when {
    cinemas.isEmpty() -> null
    cinemas.size <= 2 -> cinemas.joinToString(", ")
    else -> "${cinemas.take(2).joinToString(", ")} +${cinemas.size - 2}"
}

@Composable
private fun formatRating(rating: Double): String =
    String.format(LocalLocale.current.platformLocale, "%.1f", rating)

/**
 * Top bar of the "See all" screens: back, title and the grid/list switch (the icon shows the view
 * it switches to).
 */
@Composable
fun BrowseTopBar(
    title: String,
    viewMode: BrowseViewMode,
    onNavigateBack: () -> Unit,
    onToggleViewMode: () -> Unit
) {
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleViewMode) {
            if (viewMode == BrowseViewMode.GRID) {
                Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = stringResource(R.string.browse_view_as_list))
            } else {
                Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.browse_view_as_grid))
            }
        }
    }
}

/**
 * Title search + genre chips (multi-select). `extraFilters` is a slot for screen-specific filters
 * (e.g. cinema/chain, Now/Upcoming and sorting on the In cinemas screen).
 */
@Composable
fun BrowseFilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    genres: List<Genre>,
    selectedGenreIds: Set<Int>,
    onToggleGenre: (Int) -> Unit,
    modifier: Modifier = Modifier,
    extraFilters: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.browse_search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.browse_clear_search))
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        if (genres.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(genres, key = { it.id }) { genre ->
                    FilterChip(
                        selected = genre.id in selectedGenreIds,
                        onClick = { onToggleGenre(genre.id) },
                        label = { Text(genre.name) }
                    )
                }
            }
        }
        extraFilters?.invoke()
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * The movies in the selected view mode. The optional `onLoadMore` is called when the user gets near
 * the end (same derivedStateOf pattern as the infinite scroll on HomeScreen).
 */
@Composable
fun BrowseMovieList(
    movies: List<BrowseMovie>,
    viewMode: BrowseViewMode,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    if (viewMode == BrowseViewMode.GRID) {
        val gridState = rememberLazyGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) onLoadMore?.invoke()
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(movies, key = { it.tmdbId }) { movie ->
                BrowseGridCard(movie = movie, onClick = { onMovieClick(movie.tmdbId) })
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) { LoadingMoreFooter() }
            }
        }
    } else {
        val listState = rememberLazyListState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) onLoadMore?.invoke()
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier.fillMaxSize()
        ) {
            items(movies, key = { it.tmdbId }) { movie ->
                BrowseListRow(movie = movie, onClick = { onMovieClick(movie.tmdbId) })
            }
            if (isLoadingMore) {
                item { LoadingMoreFooter() }
            }
        }
    }
}

@Composable
private fun LoadingMoreFooter() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

/** Large card for the 2-column grid: a 2:3 poster across the column width plus basic info. */
@Composable
fun BrowseGridCard(movie: BrowseMovie, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(MlPosterCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (movie.posterPath != null) {
                AsyncImage(
                    model = tmdbPosterUrl(movie.posterPath, "w342"),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = movie.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        RatingYearLine(movie = movie, includeRuntime = false)
        if (movie.genreNames.isNotEmpty()) {
            Text(
                text = movie.genreNames.take(2).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        movie.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * List row: poster on the left; title, rating/year/runtime, genres, a 3-line overview and cinemas
 * on the right.
 */
@Composable
fun BrowseListRow(movie: BrowseMovie, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(150.dp)
                .clip(RoundedCornerShape(MlPosterCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (movie.posterPath != null) {
                AsyncImage(
                    model = tmdbPosterUrl(movie.posterPath, "w185"),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movie.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            RatingYearLine(movie = movie, includeRuntime = true)
            if (movie.genreNames.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    movie.genreNames.take(2).forEach { GenreChip(text = it) }
                }
            }
            movie.overview?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            movie.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** "★ 8.4 · 2026 · 155 min" - missing parts are skipped. */
@Composable
private fun RatingYearLine(movie: BrowseMovie, includeRuntime: Boolean) {
    val ratingText = if (movie.rating > 0) formatRating(movie.rating) else null
    val runtime = movie.runtimeMinutes?.takeIf { includeRuntime && it > 0 }
    val runtimeText = runtime?.let { stringResource(R.string.movie_detail_runtime_minutes, it) }
    val parts = listOfNotNull(ratingText, movie.year, runtimeText)
    if (parts.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        if (movie.rating > 0) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Content below the filters: a spinner on the first load, an error with "Try again", an empty state
 * (with a separate message when filters are active) or the list - all inside pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseStateContent(
    movies: List<BrowseMovie>,
    viewMode: BrowseViewMode,
    isLoading: Boolean,
    isRefreshing: Boolean,
    error: String?,
    hasActiveFilters: Boolean,
    emptyTitle: String,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMovieClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            isLoading && movies.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && movies.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ErrorCard(message = error, onRetry = onRetry)
            }
            movies.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                EmptyStateCard(
                    title = if (hasActiveFilters) stringResource(R.string.browse_empty_filtered) else emptyTitle,
                    description = if (hasActiveFilters) stringResource(R.string.browse_empty_filtered_hint) else null
                )
            }
            else -> BrowseMovieList(
                movies = movies,
                viewMode = viewMode,
                onMovieClick = onMovieClick,
                isLoadingMore = isLoadingMore,
                onLoadMore = onLoadMore
            )
        }
    }
}
