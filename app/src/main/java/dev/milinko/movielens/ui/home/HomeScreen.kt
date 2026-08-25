package dev.milinko.movielens.ui.home
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
// ...existing imports...
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
// ...existing code...
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.MoviePosterCard
import dev.milinko.movielens.ui.components.MovieneeWordmark
import dev.milinko.movielens.ui.components.SectionHeader
import kotlinx.coroutines.launch
// ...existing code...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Ako popularni filmovi nisu uspeli da se učitaju na paljenje app-a (npr. hladan start
    // backend-a) ovo je jedina prilika da se ponovo pokuša - HomeViewModel inače živi celu sesiju
    // pa mu se init{} poziva samo jednom.
    LaunchedEffect(Unit) {
        viewModel.ensureTrendingLoaded()
    }

    Scaffold(
        floatingActionButton = {
            // Dorada (17. avgust 2026, korisnička žalba "nema potrebe za lupicom") - FAB za
            // pretragu uklonjen, pretraga je već dostupna preko donje navigacije (Search tab) pa
            // je FAB bio suvišan drugi ulaz za istu akciju. Zamenjen dugmetom "nazad na vrh" koje
            // se pojavljuje tek kad korisnik dovoljno odskroluje nadole (isti obrazac kao većina
            // aplikacija sa dugim skrolom) - nema razloga da zauzima prostor dok je vrh liste već
            // vidljiv. `SmallFloatingActionButton` sa `surfaceVariant` bojom (ne crveni `primary`
            // glow rezervisan za glavne CTA akcije, videti MOVIELENS_DESIGN_PLAN.md 1.3) jer je ovo
            // sporedna, ne primarna akcija.
            val showScrollToTop by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 2 }
            }
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                SmallFloatingActionButton(
                    onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.home_scroll_to_top_content_description)
                    )
                }
            }
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = BottomNavTab.HOME,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomNavTab.HOME -> {}
                        BottomNavTab.SEARCH -> onNavigateToSearch()
                        BottomNavTab.LIBRARY -> onNavigateToLibrary()
                        BottomNavTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        }
    ) { paddingValues ->
        // Beskonačan skrol: kad je poslednja vidljiva stavka blizu kraja liste, tražimo sledeću
        // TMDB stranicu. derivedStateOf da se ovo ne preračunava na svaki recompose, samo kad se
        // stvarno promeni indeks poslednje vidljive stavke.
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                totalItems > 0 && lastVisibleIndex >= totalItems - 3
            }
        }
        LaunchedEffect(shouldLoadMore) {
            if (shouldLoadMore) {
                viewModel.loadMoreTrending()
            }
        }

        // Swipe-down da ponovo učita sve trake odjednom (trending, personalizovano, u bioskopima
        // sada, watchlist) - standardna Material3 gesta, ne zahteva poseban dugme na Home-u
        // (za razliku od Movie Night ekrana koji zadržava i eksplicitno dugme).
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshAll() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                HomeHeader()
            }

            // Search / scan lives on the Search screen. Home keeps a quick FAB for search.

            // Dizajn dorada (17. avgust 2026, korisnička žalba "izgleda nabacano") - trake su
            // ranije bile samo tekstualni naslov + red kartica direktno na pozadini, bez ikakve
            // vizuelne granice između sekcija. Svaka traka je sad u ElevatedCard-u - ISTI obrazac
            // koji već koriste Profile (SettingsSection, FriendCard) i Library ekrani ("elevacija
            // preko svetline površine, ne senki", videti MOVIELENS_DESIGN_PLAN.md 1.2/1.3) - Home
            // je bio jedini ekran koji taj obrazac nije koristio. "Popularno" ostaje van kartice
            // (plain lista, kao Library watchlist/history) jer je to glavni, dugačak, beskonačno-
            // skrolujući sadržaj, ne kratka traka za pregled - razdvojen je debljom linijom
            // (HorizontalDivider) umesto kartice da se ne pravi jedna ogromna kartica koja raste
            // unedogled.
            if (uiState.personalizedMovies.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.home_section_personalized),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.personalizedMovies, key = { it.tmdbId }) { movie ->
                                    PersonalizedMovieCard(movie = movie, onClick = { onNavigateToDetail(movie.tmdbId) })
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.nowInCinemas.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.home_section_now_in_cinemas),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.nowInCinemas, key = { it.tmdbId }) { movie ->
                                    NowInCinemasCard(movie = movie, onClick = { onNavigateToDetail(movie.tmdbId) })
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.watchlistPreview.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.home_section_continue_watching),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.watchlistPreview, key = { it.tmdbId }) { item ->
                                    WatchlistPreviewCard(item = item, onClick = { onNavigateToDetail(item.tmdbId) })
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(20.dp))
                SectionHeader(
                    title = stringResource(R.string.home_section_popular),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (uiState.isTrendingLoading && uiState.trendingMovies.isEmpty()) {
                // Prvi ikad ulazak / hladan start - spinner umesto belog/praznog ekrana dok
                // se prva strana ne učita.
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(uiState.trendingMovies, key = { it.tmdbId }) { movie ->
                    MovieListRow(
                        title = movie.title,
                        posterPath = movie.posterPath,
                        metaText = movie.releaseDate?.take(4),
                        ratingText = "${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}/10",
                        onClick = { onNavigateToDetail(movie.tmdbId) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        }
    }

    // Home only shows trending items and keeps FAB to quick-open Search screen.
}


// ...existing code...

@Composable
private fun HomeHeader() {
    // Faza 4 redizajna: wordmark je ranije bio 28sp Bold - dominirao je ekran (videti
    // MOVIELENS_DESIGN_PLAN.md 2.2: "manji i tanji ... pomera fokus na sadržaj"). Sad koristi
    // `titleLarge` (20sp SemiBold) iz teme, akcentna boja se zadržava radi prepoznatljivosti brenda.
    Column {
        // Rebrending (25. avgust 2026) - dvobojni "movie‑nee" wordmark umesto jednobojnog app_name
        // teksta (isti akcentni token kao pre, samo sad "movie" deo ostaje u tekst boji a "nee" nosi
        // akcent - videti MovieneeWordmark.kt).
        MovieneeWordmark(style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.home_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Faza 4 redizajna: PosterCard/MovieCard privatni composable-i su uklonjeni - zamenjeni deljenim
 * `MoviePosterCard`/`MovieListRow` (Faza 2, videti MOVIELENS_DESIGN_PLAN.md 1.4). Ovi tanki
 * wrapper-i samo mapiraju svaki model na gole parametre komponente (nema jedinstven "movie" model
 * u appu). Takođe uklonjen mrtav privatni `ErrorCard` - nije se nigde pozivao, postoji već
 * zajednička verzija u `ui/components/StateCards.kt`.
 */
@Composable
private fun PersonalizedMovieCard(
    movie: dev.milinko.movielens.data.remote.movie.MovieSearchResult,
    onClick: () -> Unit
) {
    MoviePosterCard(
        title = movie.title,
        posterPath = movie.posterPath,
        caption = "★ ${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}",
        onClick = onClick
    )
}

@Composable
private fun NowInCinemasCard(movie: NowInCinemasMovie, onClick: () -> Unit) {
    MoviePosterCard(
        title = movie.title,
        posterPath = movie.posterPath,
        caption = movie.cinemas.firstOrNull(),
        onClick = onClick
    )
}

@Composable
private fun WatchlistPreviewCard(item: WatchlistItem, onClick: () -> Unit) {
    MoviePosterCard(
        title = item.title,
        posterPath = item.posterPath,
        onClick = onClick
    )
}



