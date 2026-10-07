package dev.milinko.movielens.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.ui.browse.BrowseFilterBar
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.EmptyStateCard
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.RatingStars
import dev.milinko.movielens.ui.components.SignInRequiredState
import kotlinx.coroutines.launch

/**
 * Faza 7 redizajna (MOVIELENS_DESIGN_PLAN.md 2.5). Tab indikator nasleđuje `colorScheme.primary`
 * od Faze 1 (Theme.kt) - nema custom indicator koda.
 *
 * UX dorada (oktobar 2026): zajednička traka filtera (naziv + žanrovi) iznad oba taba, sortiranje
 * po tabu (i "moja ocena N+" na Odgledano), "Poništi" snackbar za uklanjanje sa liste, potvrda za
 * izmenu ocene, i prikaz greške učitavanja sa "Pokušaj ponovo".
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSignIn: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

    // Uklanjanje koje korisnik nije poništio izvršava se i kad napusti ekran pre isteka snackbar-a.
    DisposableEffect(Unit) {
        onDispose { viewModel.commitAllPendingRemovals() }
    }

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.WatchlistItemRemoved -> coroutineScope.launch {
                    // Novi "Poništi" zatvara prethodni (koji se tada izvršava) umesto da čeka u redu.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.library_removed_snackbar, event.title),
                        actionLabel = context.getString(R.string.library_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoRemove(event.tmdbId)
                    } else {
                        viewModel.commitRemove(event.tmdbId)
                    }
                }
                is LibraryEvent.HistoryItemRemoved -> coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.library_removed_from_history_snackbar, event.title),
                        actionLabel = context.getString(R.string.library_undo),
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoRemoveFromHistory(event.tmdbId)
                    } else {
                        viewModel.commitRemoveFromHistory(event.tmdbId)
                    }
                }
                is LibraryEvent.Message -> coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(context.getString(event.messageRes))
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Custom Row umesto M3 TopAppBar - statusBarsPadding jer MainActivity zove enableEdgeToEdge().
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                SignInRequiredState(
                    title = stringResource(R.string.library_sign_in_required),
                    message = stringResource(R.string.library_sign_in_required_message),
                    buttonText = stringResource(R.string.profile_sign_in),
                    onSignInClick = onNavigateToSignIn
                )
            } else {
                LibraryContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToSearch = onNavigateToSearch
                )
            }
        }
    }
}

/** Tabovi + filteri + liste (prikazuje se samo prijavljenom korisniku). */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LibraryContent(
    uiState: LibraryUiState,
    viewModel: LibraryViewModel,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToSearch: () -> Unit
) {
            val coroutineScope = rememberCoroutineScope()
            val pagerState = rememberPagerState(pageCount = { 2 })

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

            uiState.error?.let { message ->
                ErrorCard(
                    message = message,
                    onRetry = viewModel::retry,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Filteri tek kad lista ima šta da filtrira - na praznoj Biblioteci samo smetaju.
            if (uiState.watchlistTotal + uiState.historyTotal > 0) {
                BrowseFilterBar(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    genres = uiState.availableGenres,
                    selectedGenreIds = uiState.selectedGenreIds,
                    onToggleGenre = viewModel::onToggleGenre,
                    modifier = Modifier.padding(top = 8.dp),
                    extraFilters = {
                        if (pagerState.currentPage == 0) {
                            WatchlistSortRow(sort = uiState.watchlistSort, onSortChange = viewModel::onWatchlistSortChange)
                        } else {
                            HistoryFilterRow(
                                minMyRating = uiState.minMyRating,
                                onMinMyRatingChange = viewModel::onMinMyRatingChange,
                                sort = uiState.historySort,
                                onSortChange = viewModel::onHistorySortChange
                            )
                        }
                    }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> LibraryTabContent(
                        isLoading = uiState.isLoading,
                        totalCount = uiState.watchlistTotal,
                        hasError = uiState.error != null,
                        hasActiveFilters = uiState.hasActiveFilters,
                        emptyTitle = stringResource(R.string.library_watchlist_empty_title),
                        emptyMessage = stringResource(R.string.library_watchlist_empty_message),
                        onFindMovies = onNavigateToSearch
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(uiState.watchlist, key = { it.tmdbId }) { item ->
                                WatchlistItemRow(
                                    item = item,
                                    onMovieClick = { onNavigateToDetail(item.tmdbId) },
                                    onRemoveClick = { viewModel.requestRemove(item) }
                                )
                            }
                            emptyFilterResult(uiState.watchlist.isEmpty())
                        }
                    }
                    1 -> LibraryTabContent(
                        isLoading = uiState.isLoading,
                        totalCount = uiState.historyTotal,
                        hasError = uiState.error != null,
                        hasActiveFilters = uiState.hasActiveFilters,
                        emptyTitle = stringResource(R.string.library_history_empty_title),
                        emptyMessage = stringResource(R.string.library_history_empty_message),
                        onFindMovies = onNavigateToSearch
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            items(uiState.history, key = { it.tmdbId }) { item ->
                                HistoryItemRow(
                                    item = item,
                                    onMovieClick = { onNavigateToDetail(item.tmdbId) },
                                    onRate = { stars -> viewModel.rateMovie(item.tmdbId, stars) },
                                    onRemoveClick = { viewModel.requestRemoveFromHistory(item) }
                                )
                            }
                            emptyFilterResult(uiState.history.isEmpty())
                        }
                    }
                }
            }
}

/** Kad filteri ne pogađaju ništa (a lista sama nije prazna). */
private fun androidx.compose.foundation.lazy.LazyListScope.emptyFilterResult(isEmpty: Boolean) {
    if (!isEmpty) return
    item {
        EmptyStateCard(
            title = stringResource(R.string.browse_empty_filtered),
            description = stringResource(R.string.browse_empty_filtered_hint),
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

/**
 * Stanja jednog taba: spinner pri prvom učitavanju, prazna lista (sa "Pronađi filmove"), ili sadržaj.
 * Prazno stanje se ne prikazuje kad je lista prazna zbog greške - tada je iznad već ErrorCard.
 */
@Composable
private fun LibraryTabContent(
    isLoading: Boolean,
    totalCount: Int,
    hasError: Boolean,
    hasActiveFilters: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    onFindMovies: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        isLoading && totalCount == 0 -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        totalCount == 0 && hasError -> Box(modifier = Modifier.fillMaxSize())
        totalCount == 0 -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            EmptyStateCard(
                title = emptyTitle,
                description = emptyMessage,
                actionText = stringResource(R.string.library_find_movies),
                onAction = onFindMovies
            )
        }
        else -> content()
    }
}

@Composable
private fun WatchlistSortRow(sort: WatchlistSort, onSortChange: (WatchlistSort) -> Unit) {
    SortMenuRow(
        options = listOf(
            WatchlistSort.ADDED to stringResource(R.string.library_sort_added),
            WatchlistSort.TMDB_RATING to stringResource(R.string.library_sort_tmdb_rating),
            WatchlistSort.TITLE to stringResource(R.string.library_sort_title),
            WatchlistSort.YEAR to stringResource(R.string.library_sort_year)
        ),
        selected = sort,
        onSelect = onSortChange,
        leading = {}
    )
}

@Composable
private fun HistoryFilterRow(
    minMyRating: Int?,
    onMinMyRatingChange: (Int?) -> Unit,
    sort: HistorySort,
    onSortChange: (HistorySort) -> Unit
) {
    SortMenuRow(
        options = listOf(
            HistorySort.WATCHED to stringResource(R.string.library_sort_watched),
            HistorySort.MY_RATING to stringResource(R.string.library_sort_my_rating),
            HistorySort.TMDB_RATING to stringResource(R.string.library_sort_tmdb_rating),
            HistorySort.TITLE to stringResource(R.string.library_sort_title)
        ),
        selected = sort,
        onSelect = onSortChange,
        leading = {
            // "Moja ocena 7+ / 8+ / 9+" - brz filter za "nešto što sam stvarno voleo".
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(7, 8, 9)) { threshold ->
                    FilterChip(
                        selected = minMyRating == threshold,
                        onClick = { onMinMyRatingChange(threshold) },
                        label = { Text(stringResource(R.string.library_min_rating_chip, threshold)) }
                    )
                }
            }
        }
    )
}

/** Red sa opcionim levim sadržajem i dugmetom za sortiranje (meni) desno. */
@Composable
private fun <T> SortMenuRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    leading: @Composable () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) { leading() }
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.browse_sort_content_description))
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                options.forEach { (option, label) ->
                    DropdownMenuItem(
                        text = { Text(if (option == selected) "✓ $label" else label) },
                        onClick = {
                            onSelect(option)
                            menuOpen = false
                        }
                    )
                }
            }
        }
    }
}

/** "2024 · Komedija, Drama" - delovi koji nedostaju se preskaču. */
private fun metaLine(year: Int?, genres: List<dev.milinko.movielens.data.remote.movie.Genre>?): String? =
    listOfNotNull(
        year?.toString(),
        genres.orEmpty().take(2).joinToString(", ") { it.name }.takeIf { it.isNotBlank() }
    ).joinToString(" · ").takeIf { it.isNotBlank() }

@Composable
private fun formatRating(value: Double): String =
    String.format(LocalLocale.current.platformLocale, "%.1f", value)

@Composable
private fun WatchlistItemRow(
    item: WatchlistItem,
    onMovieClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    MovieListRow(
        title = item.title,
        posterPath = item.posterPath,
        metaText = metaLine(item.releaseYear, item.genres),
        ratingText = item.voteAverage?.takeIf { it > 0 }?.let { stringResource(R.string.library_tmdb_rating, formatRating(it)) },
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
 * Olovka otvara/zatvara red od 10 zvezdica za brzu izmenu ocene, bez odlaska na Movie Detail -
 * sam `RatingStars` red se širi ISPOD reda (stateful UI ostaje u ekranu, ne u deljenoj komponenti).
 */
@Composable
private fun HistoryItemRow(
    item: HistoryItem,
    onMovieClick: () -> Unit,
    onRate: (Int) -> Unit,
    onRemoveClick: () -> Unit
) {
    var isEditingRating by remember { mutableStateOf(false) }

    Column {
        MovieListRow(
            title = item.title,
            posterPath = item.posterPath,
            metaText = metaLine(item.releaseYear, item.genres),
            description = item.voteAverage?.takeIf { it > 0 }?.let { stringResource(R.string.library_tmdb_rating, formatRating(it)) },
            ratingText = if (item.userRating > 0) {
                stringResource(R.string.library_my_rating, formatRating(item.userRating))
            } else {
                stringResource(R.string.library_not_rated)
            },
            onClick = onMovieClick,
            trailing = {
                Row {
                    IconButton(onClick = { isEditingRating = !isEditingRating }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.library_rating_edit_content_description),
                            tint = if (isEditingRating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Uklanjanje iz odgledanih (briše i ocenu) - sa "Poništi" snackbar-om, kao na
                    // listi za gledanje. Ranije je bilo moguće samo sa detalja filma.
                    IconButton(onClick = onRemoveClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.library_remove_from_history_content_description),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
        if (isEditingRating) {
            RatingStars(
                rating = item.userRating.toInt(),
                onRate = { stars ->
                    onRate(stars)
                    isEditingRating = false
                },
                modifier = Modifier.padding(start = 72.dp, bottom = 8.dp)
            )
        }
    }
}
