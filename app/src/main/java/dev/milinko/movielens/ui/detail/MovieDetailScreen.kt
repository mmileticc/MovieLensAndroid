package dev.milinko.movielens.ui.detail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.CastMember
import dev.milinko.movielens.data.remote.movie.MovieComment
import dev.milinko.movielens.data.remote.movie.Showtime
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.AppButtonVariant
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.FullScreenImageDialog
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.RatingStars
import dev.milinko.movielens.ui.components.RemoteAvatarImage
import dev.milinko.movielens.ui.components.SectionHeader

/** Koliko bioskopa se uvek prikazuje pre "Prikaži još N" - videti dorada posle Faze 6 feedback-a. */
private const val ALWAYS_VISIBLE_SHOWTIMES = 3

// E2.2 dorada (3. septembar 2026) - mora se poklapati sa MovieDetailViewModel.COMMENTS_PAGE_SIZE
// (privatna tamo, pa se ovde ponavlja isti broj - isti obrazac kao ALWAYS_VISIBLE_SHOWTIMES iznad).
// Koristi se samo za "Prikaži manje" prag (kad je vidljivo više od jedne stranice).
//
// Dorada (3. septembar 2026, korisnički bug report - "dugme za više/manje ne radi kako sam
// očekivao, ovako ih sve izlistava i zatrpava prikaz kad je dosta komentara") - stvaran uzrok:
// stranica od 10 je bila prevelika, pa se dugmad NIKAD nisu ni pojavila dok korisnik nije imao
// preko 10 komentara na jednom filmu - do tada je "Prikaži više/manje" bilo potpuno nevidljivo,
// baš kao da ne postoji. Smanjeno na 3 (isti broj kao ALWAYS_VISIBLE_SHOWTIMES) - dugmad se sad
// pojavljuju znatno ranije, čim ima i malo više komentara.
private const val COMMENTS_PAGE_SIZE = 3

@Composable
fun MovieDetailScreen(
    tmdbId: Int,
    viewModel: MovieDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onRequireSignIn: () -> Unit = {},
    onNavigateToFriendProfile: (String) -> Unit = {},
    // E2.3 (2. septembar 2026) - klik na glumca u "Cast" traci vodi na njegovu stranicu.
    onNavigateToActor: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // "Sortiraj po udaljenosti" dugme u sekciji bioskopa - traži dozvolu tek na klik (ne odmah pri
    // otvaranju ekrana), da ne nagovaramo korisnika za dozvolu na filmovima koji možda i nemaju
    // bioskopske termine.
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.loadMovieDetail(tmdbId)
    }

    // Fullscreen pregled postera na klik (kao u drugim movie/photo aplikacijama) - poster je inače
    // ContentScale.Crop u fiksnoj visini pa se seče, ovde se vidi ceo + može da se zumira.
    var showFullPoster by remember { mutableStateOf(false) }

    LaunchedEffect(tmdbId) {
        viewModel.loadMovieDetail(tmdbId)
    }

    // Bugfix (E1.2, 2. septembar 2026, feedback testera - ružna "Update history failed: 404"
    // poruka pri brzim uzastopnim klikovima) - watchlistError/historyError polja i ovi Toast-ovi su
    // namerno uklonjeni, videti MovieDetailViewModel.kt (historyJob/watchlistJob napomena) - greške
    // iz ovog toka se sad samo tiho loguju, nikad ne prekidaju korisnika usred klika.

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onNavigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.movie_detail_top_bar_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                // Share dugme (16. avgust 2026) - deli naslov + TMDB link preko standardnog Android
                // Share Intent-a. Onemogućeno dok se detalji filma ne učitaju (treba nam naslov).
                val movieForShare = uiState.movieDetail
                IconButton(
                    onClick = {
                        val shareText = context.getString(
                            R.string.movie_detail_share_text,
                            movieForShare?.title.orEmpty(),
                            "https://www.themoviedb.org/movie/$tmdbId"
                        )
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    },
                    enabled = movieForShare != null
                ) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.movie_detail_share_content_description))
                }
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.error_generic_title),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Faza B bugfix - uiState.error!! je nepotreban non-null assertion (uslov iznad
                // `else if (uiState.error != null)` ga već garantuje, ali `?:` je jeftinije i
                // otpornije na buduće refaktorisanje ovog if-lanca).
                Text(uiState.error ?: stringResource(R.string.error_generic_title))
                Spacer(modifier = Modifier.height(16.dp))
                AppButton(
                    text = stringResource(R.string.movie_detail_retry),
                    onClick = { viewModel.loadMovieDetail(tmdbId) }
                )
            }
        } else if (uiState.movieDetail != null) {
            // Faza B bugfix (16. avgust 2026) - ranije `uiState.movieDetail!!` ponovljeno 9+ puta
            // (svaki `item{}` blok posebno) - jedan `!!` OVDE (odmah posle već proverenog uslova
            // gore) i dalje se koristi `movie` svuda ispod, umesto da svaki nested blok sam ponavlja
            // istu proveru/pretpostavku.
            val movie = uiState.movieDetail!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Faza 5 redizajna (videti MOVIELENS_DESIGN_PLAN.md 2.4): poster + naslov su ranije
                // bili dva odvojena item bloka (poster pa ispod običan tekst). Sad su spojeni u
                // jedan "hero" - naslov/godina/trajanje se ispisuju PREKO donjeg dela slike uz
                // "scrim" gradijent (transparent -> background) radi čitljivosti, filmskiji izgled
                // i štedi vertikalni prostor. Ocena/dugmad/RatingStars/prijatelji ostaju ispod hero-a
                // kao običan sadržaj (doc eksplicitno pominje samo naslov/godina/trajanje na slici).
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(340.dp)) {
                        if (movie.posterPath != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(tmdbPosterUrl(movie.posterPath))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = movie.title,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showFullPoster = true },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        // Frakcioni color stop-ovi (ne raw piksel startY) - nezavisno od gustine
                        // ekrana, "scrim" uvek počinje od pola visine hero-a naniže.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Transparent,
                                            0.5f to Color.Transparent,
                                            1f to MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = movie.title,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                Text(
                                    text = movie.releaseDate?.take(4) ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.movie_detail_runtime_minutes, movie.runtime),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Dorada (3. septembar 2026, korisnički zahtev - "žanrovi treba da stoje pre
                // komentara i moje ocene, a verovatno i pre watchliste/oznaci-kao-odgledano" - žanr
                // je brz vizuelni kontekst o filmu, ima smisla da se vidi ODMAH ispod hero-a, pre
                // interaktivnih dugmadi/ocene/komentara koji su niže niz ekran) - premešteno sa
                // ranije pozicije (bilo je ispod svega, između komentara i bioskopskih termina).
                item {
                    if (movie.genres.isNotEmpty()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.movie_detail_genres_header),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                movie.genres.take(3).forEach { genre ->
                                    GenreChip(text = genre.name)
                                }
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}/10",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.movie_detail_tmdb_rating_source),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        // Dopunske ocene (3. septembar 2026) - IMDb i Rotten Tomatoes, dolaze sa
                        // OMDb-a preko backend-a. Ceo red se ne prikazuje ako oba polja nedostaju
                        // (npr. OMDB_API_KEY nije podešen na serveru).
                        if (movie.imdbRating != null || movie.rottenTomatoes != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                movie.imdbRating?.let { imdb ->
                                    ExternalRatingBadge(
                                        label = "IMDb",
                                        value = String.format(LocalLocale.current.platformLocale, "%.1f/10", imdb),
                                        badgeColor = Color(0xFFF5C518),
                                        textColor = Color(0xFF000000)
                                    )
                                }
                                movie.rottenTomatoes?.let { rt ->
                                    ExternalRatingBadge(
                                        label = "RT",
                                        value = rt,
                                        badgeColor = Color(0xFFFA320A),
                                        textColor = Color(0xFFFFFFFF)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppButton(
                                text = if (uiState.isInWatchlist) stringResource(R.string.movie_detail_watchlist_added) else stringResource(R.string.movie_detail_watchlist_add),
                                leadingIcon = if (uiState.isInWatchlist) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                variant = AppButtonVariant.Primary,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val startedRequest = viewModel.toggleWatchlist(tmdbId)
                                    if (!startedRequest) {
                                        Toast.makeText(context, context.getString(R.string.movie_detail_watchlist_signin_toast), Toast.LENGTH_SHORT).show()
                                        onRequireSignIn()
                                    }
                                }
                            )

                            AppButton(
                                text = if (uiState.isWatched) stringResource(R.string.movie_detail_watched) else stringResource(R.string.movie_detail_mark_watched),
                                leadingIcon = if (uiState.isWatched) Icons.Default.CheckCircle else Icons.Default.Visibility,
                                variant = AppButtonVariant.Secondary,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val startedRequest = viewModel.toggleWatched(tmdbId)
                                    if (!startedRequest) {
                                        Toast.makeText(context, context.getString(R.string.movie_detail_watched_signin_toast), Toast.LENGTH_SHORT).show()
                                        onRequireSignIn()
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.userRating > 0) {
                                stringResource(R.string.movie_detail_your_rating_value, uiState.userRating.toInt())
                            } else {
                                stringResource(R.string.movie_detail_your_rating)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RatingStars(
                            rating = uiState.userRating.toInt(),
                            onRate = { stars ->
                                val startedRequest = viewModel.rateMovie(tmdbId, stars)
                                if (!startedRequest) {
                                    Toast.makeText(context, context.getString(R.string.movie_detail_rate_signin_toast), Toast.LENGTH_SHORT).show()
                                    onRequireSignIn()
                                }
                            }
                        )

                        if (uiState.friendsWatched.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FriendsWatchedBadge(
                                friends = uiState.friendsWatched,
                                onFriendClick = onNavigateToFriendProfile
                            )
                        }
                    }
                }

                // E2.2 dorada (3. septembar 2026) - javni komentari, zamenjuju stari privatan
                // "komentar uz ocenu" koji je bio u item-u iznad (i dalje sadrži samo ocenu).
                item {
                    CommentsSection(
                        tmdbId = tmdbId,
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequireSignIn = onRequireSignIn
                    )
                }

                if (movie.showtimes.isNotEmpty()) {
                    item {
                        val showtimes = movie.showtimes
                        // Dorada posle Faze 6 feedback-a: lista bioskopa je bila ravna i nepregledna
                        // kad ih ima dosta - isti obrazac sažimanja kao sekundarni pogoci na Search
                        // ekranu (prvih ALWAYS_VISIBLE_SHOWTIMES uvek vidljivo, ostatak iza
                        // "Prikaži još N" pomoću ExpandableMatchCard).
                        val alwaysVisible = showtimes.take(ALWAYS_VISIBLE_SHOWTIMES)
                        val remaining = showtimes.drop(ALWAYS_VISIBLE_SHOWTIMES)
                        var showtimesExpanded by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.movie_detail_showtimes_header),
                                actionText = if (!hasLocationPermission) stringResource(R.string.movie_detail_sort_by_distance) else null,
                                onActionClick = if (!hasLocationPermission) {
                                    { locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                                } else null
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                alwaysVisible.forEach { show ->
                                    CinemaShowtimeCard(show = show)
                                }
                            }
                            if (remaining.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                ExpandableMatchCard(
                                    expanded = showtimesExpanded,
                                    onToggleExpanded = { showtimesExpanded = !showtimesExpanded },
                                    header = {
                                        Text(
                                            text = if (showtimesExpanded) {
                                                stringResource(R.string.movie_detail_hide_showtimes)
                                            } else {
                                                stringResource(R.string.movie_detail_show_more_showtimes, remaining.size)
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    expandedContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            remaining.forEach { show ->
                                                CinemaShowtimeCard(show = show)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.movie_detail_overview_header),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        movie.overview?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    }
                }

                if (movie.cast.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.movie_detail_cast_header),
                            modifier = Modifier.padding(16.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(movie.cast.take(6)) { actor ->
                                CastAvatarCard(actor = actor, onClick = { onNavigateToActor(actor.id) })
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (showFullPoster && movie.posterPath != null) {
                FullScreenImageDialog(
                    imageUrl = tmdbPosterUrl(movie.posterPath) ?: "",
                    onDismiss = { showFullPoster = false }
                )
            }
        }
    }
}

/**
 * Kartica bioskopa u "Bioskopski termini" sekciji. Faza 5 redizajna (MOVIELENS_DESIGN_PLAN.md
 * 2.4): dodata ikonica lokacije + jasnija hijerarhija (naziv bioskopa `titleMedium`, adresa/
 * udaljenost sitnije ispod). Napomena: doc pominje "vreme kao chip", ali `Showtime` model NEMA
 * eksplicitno polje za vreme termina (samo cinemaName/cinemaLocation/bookingUrl/distanceKm/
 * isUpcoming - videti MovieModels.kt) - umesto toga, "Uskoro" bedž (jedino dostupno statusno polje)
 * je taj koji sada koristi `GenreChip` stil umesto običnog teksta, u duhu iste ideje.
 */
@Composable
private fun CinemaShowtimeCard(show: Showtime) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = show.cinemaName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = show.cinemaLocation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (show.distanceKm != null) {
                    Text(
                        text = "${String.format(LocalLocale.current.platformLocale, "%.1f", show.distanceKm)} km",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
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

/**
 * "Prijatelji su gledali ovaj film" bedž - prost upit nad postojećim WATCHED podacima (bez
 * ikakvog match algoritma, to je Faza 4). Klik na ime prijatelja vodi na njegov profil.
 */
@Composable
private fun FriendsWatchedBadge(friends: List<FriendWatched>, onFriendClick: (String) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (friends.size == 1) {
                    stringResource(R.string.movie_detail_friends_watched_one)
                } else {
                    stringResource(R.string.movie_detail_friends_watched_many, friends.size)
                },
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            friends.forEach { friend ->
                // E2.2 dorada (3. septembar 2026) - kratak tekstualni komentar prijatelja je
                // ranije bio prikazan ovde (isto `review` polje kao HistoryItem) - to polje je
                // ZAMENJENO javnim komentarima ispod (CommentsSection), pa je prikaz uklonjen -
                // ostaje samo ime + ocena.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFriendClick(friend.friendId) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = friend.friendName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (friend.rating != null) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${friend.rating}/10",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Avatar kartica za horizontalnu traku glumaca (Faza 5 redizajna, videti MOVIELENS_DESIGN_PLAN.md
 * 2.4: "mala horizontalna traka sa okruglim avatarima + imenom ispod" - ranije je bio prost
 * vertikalan tekst red). `AsyncImage` mora imati `fillMaxSize()` PRE `clip()` (naučeno u Fazi 4 -
 * bez toga slika renderuje na malu podrazumevanu veličinu u gornjem levom uglu Box-a).
 */
@Composable
private fun CastAvatarCard(actor: CastMember, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (actor.profilePath != null) {
                AsyncImage(
                    model = tmdbPosterUrl(actor.profilePath, "w185"),
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actor.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        actor.character?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// RatingStars je premešten u ui/components/RatingStars.kt (deljena komponenta - koristi je i
// Library/Istorija tab za izmenu ocene, videti LibraryScreen.kt).

/**
 * Mali bedž za dopunsku ocenu (IMDb/Rotten Tomatoes) sa OMDb-a, prikazan pored TMDB ocene na
 * Movie Detail ekranu (3. septembar 2026). Namerno prost tekstualni bedž umesto pravih logoa
 * (nema licenciranih ikonica u projektu) - boja pozadine dovoljno jasno signalizira izvor.
 */
@Composable
private fun ExternalRatingBadge(label: String, value: String, badgeColor: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(badgeColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

/**
 * Javni komentari na film (E2.2 dorada, 3. septembar 2026) - zamenjuje stari privatan "komentar
 * uz ocenu". Sadrži polje za pisanje novog komentara + listu postojećih ("prijatelji" uvek prvi,
 * sortiranje je već urađeno na backendu - videti CommentsRepository.getForMovie) + "Prikaži više/
 * manje" dugmad. `uiState`/`viewModel` se prosleđuju direktno (umesto da se svaka funkcija
 * pojedinačno prosledi) jer sekcija treba dosta različitih polja/akcija odjednom - isti kompromis
 * kao drugi veći item blokovi na ovom ekranu.
 */
@Composable
private fun CommentsSection(
    tmdbId: Int,
    uiState: MovieDetailUiState,
    viewModel: MovieDetailViewModel,
    onRequireSignIn: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(16.dp)) {
        SectionHeader(
            title = stringResource(R.string.movie_detail_comments_header),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var commentText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = commentText,
            onValueChange = { if (it.length <= 1000) commentText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.movie_detail_comment_label)) },
            minLines = 2,
            maxLines = 4
        )
        if (commentText.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AppButton(
                text = stringResource(R.string.movie_detail_post_comment),
                variant = AppButtonVariant.Secondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    focusManager.clearFocus()
                    val submittedText = commentText
                    val startedRequest = viewModel.postComment(tmdbId, submittedText)
                    if (startedRequest) {
                        commentText = ""
                    } else {
                        Toast.makeText(context, context.getString(R.string.movie_detail_comment_post_signin_toast), Toast.LENGTH_SHORT).show()
                        onRequireSignIn()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Dorada (3. septembar 2026, korisnički zahtev - "pre brisanja komentara treba samo da
        // pita da li je siguran") - isti obrazac kao friendPendingRemoval u ProfileScreen.kt.
        var commentPendingDeletion by remember { mutableStateOf<MovieComment?>(null) }

        if (uiState.isLoadingComments) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.comments.isEmpty()) {
            Text(
                text = stringResource(R.string.movie_detail_comments_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.comments.take(uiState.commentsVisibleCount).forEach { comment ->
                    CommentCard(
                        comment = comment,
                        onLikeClick = { viewModel.toggleCommentLike(comment.id) },
                        onDeleteClick = { commentPendingDeletion = comment }
                    )
                }
            }

            val canShowMore = uiState.commentsVisibleCount < uiState.comments.size || uiState.commentsHasMoreOnServer
            val canShowLess = uiState.commentsVisibleCount > COMMENTS_PAGE_SIZE
            if (canShowMore || canShowLess) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (canShowMore) {
                        TextButton(
                            onClick = { viewModel.showMoreComments(tmdbId) },
                            enabled = !uiState.isLoadingMoreComments
                        ) {
                            if (uiState.isLoadingMoreComments) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text(stringResource(R.string.movie_detail_comments_show_more))
                            }
                        }
                    }
                    if (canShowLess) {
                        TextButton(onClick = { viewModel.showLessComments() }) {
                            Text(stringResource(R.string.movie_detail_comments_show_less))
                        }
                    }
                }
            }
        }

        commentPendingDeletion?.let { comment ->
            AlertDialog(
                onDismissRequest = { commentPendingDeletion = null },
                title = { Text(stringResource(R.string.movie_detail_comment_delete_dialog_title)) },
                text = { Text(stringResource(R.string.movie_detail_comment_delete_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteComment(comment.id)
                        commentPendingDeletion = null
                    }) {
                        Text(stringResource(R.string.movie_detail_comment_delete_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { commentPendingDeletion = null }) {
                        Text(stringResource(R.string.movie_detail_comment_delete_cancel))
                    }
                }
            )
        }
    }
}

/**
 * Jedan komentar - avatar/ime (+ "Prijatelj" bedž ako je isFriend), tekst, i lajk/brisanje na dnu.
 * Kanta za smeće se prikazuje samo za `isOwn` komentare; srce za lajk je onemogućeno za sopstveni
 * komentar (backend ionako odbija - videti CommentsRepository.toggleLike - ovde je samo vizuelna
 * potvrda iste odluke, da korisnik ne klikne pa dobije tihu grešku).
 */
@Composable
private fun CommentCard(comment: MovieComment, onLikeClick: () -> Unit, onDeleteClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Bugfix (3. septembar 2026, korisnički bug report - "slika ljudi koji su
                // komentarisali se ne prikazuje") - avatar polje je često `data:image/...;base64,...`
                // (videti util/AvatarImage.kt), a plain AsyncImage(model=url) (Coil 2.x) nema podršku
                // za `data:` šemu pa je tiho ništa ne prikazivao. RemoteAvatarImage je deljena
                // komponenta koja već rešava ovo za ostatak app-a (feed prijatelja, profil, itd.).
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    RemoteAvatarImage(
                        url = comment.avatarUrl,
                        contentDescription = comment.username,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = comment.username, style = MaterialTheme.typography.titleSmall)
                    if (comment.isFriend) {
                        Spacer(modifier = Modifier.width(6.dp))
                        GenreChip(text = stringResource(R.string.movie_detail_comment_friend_badge))
                    }
                }
                if (comment.isOwn) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.movie_detail_comment_delete_content_description),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = comment.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !comment.isOwn, onClick = onLikeClick)
            ) {
                Icon(
                    imageVector = if (comment.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.movie_detail_comment_like_content_description),
                    modifier = Modifier.size(16.dp),
                    tint = if (comment.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${comment.likeCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

