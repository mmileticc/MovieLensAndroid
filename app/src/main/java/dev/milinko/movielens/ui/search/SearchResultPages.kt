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
 * Stranica 0 pagera (25. avgust 2026 dorada, videti napomenu u [SearchScreen]) - obična tekstualna
 * pretraga (ceo TMDB katalog) + vision rezultat, isti sadržaj koji je ranije bio direktno u glavnom
 * LazyColumn-u pod `!uiState.isCinemaMode` granom - sad je to sama ova stranica, grananje po modu
 * više nije potrebno unutra jer stranica sama IMPLICIRA mod.
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
        // Faza 6 dorada (korisnikov feedback): rezultat pretrage slikom se ne gasi kad korisnik
        // počne da kuca tekstualnu pretragu - obe sekcije mogu da postoje istovremeno, naslagane.
        uiState.visionResult?.let { response ->
            item {
                VisionResultSection(
                    response = response,
                    onClear = onClearVision,
                    // Dorada #2 (17. avgust 2026) - istorija se upisuje samo kad korisnik stvarno
                    // klikne na rezultat (ne na svako kucanje).
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
                // Mala odvojena naslovna linija samo kad su OBA rezultata istovremeno na ekranu.
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
            // UX dorada (oktobar 2026): ranije je stranica tad ostajala potpuno prazna - nije se
            // razlikovalo "nema rezultata" od "još se učitava" ili "nešto je puklo".
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
 * Stranica 1 pagera (25. avgust 2026 dorada) - semantička pretraga svedena na filmove trenutno na
 * repertoaru (Faza D). Bez istorije (namerno, videti MOVIELENS_DESIGN_PLAN.md Faza D), bez vision
 * sekcije, drugačiji izvor podataka (`NowInCinemasMovie`).
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
            // Repertoar nije učitan - filter po nazivu nema nad čime da radi; ranije je ovo
            // izgledalo kao "Nema poklapanja po nazivu".
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
                // E2.1/E2.4 dorada - ova poruka se sad prikazuje i kad lokalni filter po nazivu
                // (dok korisnik kuca) ne nađe ništa, NE samo posle prave semantičke pretrage -
                // zato sad eksplicitno usmerava na dugme za slanje umesto generičkog "nema
                // poklapanja", da korisnik zna da postoji i pametnija (AI) opcija.
                // Bugfix - !isFilteringCinemasLive sprečava treperenje ove poruke tokom debounce
                // prozora (300ms) posle svakog karaktera, pre nego što lokalni filter završi.
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
 * Prazno stanje pre prve pretrage. Dorada (17. avgust 2026, korisnička žalba "prazno dok se ne
 * krene sa pretragom") - ranije je ovde stajala samo prijateljska poruka (dokument 2.3 je pominjao
 * "nedavne pretrage" kao ideju, odloženo tada jer SearchUiState nije imao to polje - sad ima, videti
 * SearchViewModel.recentSearches/RecentSearchesStore). Istorija pretraga se prikazuje kao tap-nazad
 * chip-ovi (isti obrazac kao Google/YouTube "nedavne pretrage") iznad postojeće poruke - ako istorije
 * nema (prvi ikad ulazak) prikazuje se identično kao pre ove dorade.
 */
@Composable
internal fun SearchEmptyState(
    // Faza D - title/message su sad parametri (ranije fiksni stringResource pozivi unutra) da isti
    // sastav (chip-ovi istorije + animirana ikonica) može da se reciklira i za "U bioskopima" mod
    // (koji nema istoriju - poziva se sa recentSearches = emptyList()).
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
                // FlowRow bi bio idealan ovde, ali projekat ga još ne koristi nigde - obična
                // Column od Row-ova (2 po redu) je dovoljno dobra za najviše 8 stavki
                // (RecentSearchesStore.MAX_ITEMS), bez uvođenja novog layout obrasca samo za ovo.
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

        // Dorada (17. avgust 2026, korisnička žalba "ekran deluje delimično prazan") - animirana
        // ikonica-bedž iznad poruke, isti vizuelni obrazac kao logo krug na Sign In ekranu
        // (`accentPrimary` na 13% providnosti + tanka ivica), samo ovde "diše" (blag puls skale) -
        // ista `rememberInfiniteTransition` tehnika koja već postoji u MovieNightScreen.kt za
        // rotaciju "Osveži predloge" ikonice, ovde primenjena na skalu umesto rotaciju.
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
            // Rebrending (25. avgust 2026) - stvaran Movienee znak umesto generičke
            // Icons.Default.Movie ikonice, isti tretman kao badž na Sign In ekranu.
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
