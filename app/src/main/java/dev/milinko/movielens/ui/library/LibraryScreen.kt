package dev.milinko.movielens.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.RatingStars
import kotlinx.coroutines.launch

/**
 * Faza 7 redizajna (MOVIELENS_DESIGN_PLAN.md 2.5). Napomena o tab indikatoru: dokument traži
 * "accentPrimary indikator umesto default Material boje" - to je već rešeno automatski Fazom 1
 * (Theme.kt) jer `SecondaryTabRow`/`Tab` po M3 default-u crtaju selektovani indikator/tekst u
 * `colorScheme.primary`, a taj token je od Faze 1 nadalje naša `MlAccentPrimary` (crvena) -
 * nema potrebe za custom indicator kodom, dovoljno je da tabovi ostanu nedirani.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.library_top_bar_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = BottomNavTab.LIBRARY,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomNavTab.HOME -> onNavigateToHome()
                        BottomNavTab.SEARCH -> onNavigateToSearch()
                        BottomNavTab.LIBRARY -> {}
                        BottomNavTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState.requiresSignIn) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.library_sign_in_required),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Svajp između tabova (pored klika na naslov kartice), isti obrazac kao Profile.
                val pagerState = rememberPagerState(pageCount = { 2 })
                val coroutineScope = rememberCoroutineScope()

                SecondaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.library_tab_watchlist)) }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.library_tab_history)) }
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> {
                            if (uiState.isLoading && uiState.watchlist.isEmpty()) {
                                // Prvi ikad ulazak (Room prazan, mreža još nije vratila) - spinner
                                // umesto lažne "nema filmova" poruke dok se ne zna pravo stanje.
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (uiState.watchlist.isEmpty()) {
                                LibraryEmptyState(
                                    title = stringResource(R.string.library_watchlist_empty_title),
                                    message = stringResource(R.string.library_watchlist_empty_message)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(uiState.watchlist, key = { it.tmdbId }) { item ->
                                        WatchlistItemRow(
                                            item = item,
                                            onMovieClick = { onNavigateToDetail(item.tmdbId) },
                                            onRemoveClick = { viewModel.removeFromWatchlist(item.tmdbId) }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (uiState.isLoading && uiState.history.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else if (uiState.history.isEmpty()) {
                                LibraryEmptyState(
                                    title = stringResource(R.string.library_history_empty_title),
                                    message = stringResource(R.string.library_history_empty_message)
                                )
                            } else {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(uiState.history, key = { it.tmdbId }) { item ->
                                        HistoryItemRow(
                                            item = item,
                                            onMovieClick = { onNavigateToDetail(item.tmdbId) },
                                            onRate = { stars -> viewModel.rateMovie(item.tmdbId, stars) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun WatchlistItemRow(
    item: WatchlistItem,
    onMovieClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    MovieListRow(
        title = item.title,
        posterPath = item.posterPath,
        metaText = if (item.priority > 0) stringResource(R.string.library_watchlist_priority, item.priority) else null,
        onClick = onMovieClick,
        trailing = {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.library_remove_content_description),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    )
}

/**
 * Tap na ocenu otvara/zatvara red od 10 zvezdica za brzu izmenu, bez odlaska na Movie Detail -
 * `MovieListRow`-ov `trailing` slot nosi samo mali toggle, sam `RatingStars` red se širi ISPOD
 * (dogovoreno u Fazi 2 - stateful UI ostaje u ekranu, ne u deljenoj komponenti).
 */
@Composable
private fun HistoryItemRow(
    item: HistoryItem,
    onMovieClick: () -> Unit,
    onRate: (Int) -> Unit
) {
    var isEditingRating by remember { mutableStateOf(false) }

    Column {
        MovieListRow(
            title = item.title,
            posterPath = item.posterPath,
            ratingText = "${String.format("%.1f", item.userRating)}/10",
            onClick = onMovieClick,
            trailing = {
                // Odvojen tap-target od MovieListRow-ovog glavnog onClick-a (koji vodi na Movie
                // Detail) - ovaj mali tekst samo otvara/zatvara RatingStars red ispod.
                Text(
                    text = if (isEditingRating) stringResource(R.string.library_rating_close) else stringResource(R.string.library_rating_edit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { isEditingRating = !isEditingRating }
                        .padding(4.dp)
                )
            }
        )
        if (isEditingRating) {
            RatingStars(
                rating = item.userRating.toInt(),
                onRate = { stars ->
                    onRate(stars)
                    isEditingRating = false
                },
                modifier = Modifier.padding(start = 84.dp, bottom = 8.dp)
            )
        }
    }
}

// Zajednička AppBottomNavigationBar komponenta (ui/components) — bez lokalnih duplikata.
