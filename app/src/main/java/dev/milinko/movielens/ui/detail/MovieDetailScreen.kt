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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.BuildConfig
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
import dev.milinko.movielens.ui.components.YouTubeTrailerPlayer

/** Koliko bioskopa se uvek prikazuje pre "Prikaži još N" - videti dorada posle Faze 6 feedback-a. */
private const val ALWAYS_VISIBLE_SHOWTIMES = 3

// Opis filma se skraćuje na ovoliko redova uz "Prikaži više".
private const val OVERVIEW_COLLAPSED_LINES = 5

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
    // Backdrop iz galerije "Slike" otvoren preko celog ekrana (null = zatvoreno).
    var fullBackdropPath by remember { mutableStateOf<String?>(null) }

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
                // Deljenje: link ka stranici filma (otvara aplikaciju ako je instalirana) preko
                // sistemskog Share menija, ili slanje prijatelju unutar aplikacije (push obaveštenje).
                // Onemogućeno dok se detalji filma ne učitaju (treba nam naslov).
                val movieForShare = uiState.movieDetail
                var shareMenuExpanded by remember { mutableStateOf(false) }
                var showShareWithFriend by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { shareMenuExpanded = true }, enabled = movieForShare != null) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.movie_detail_share_content_description))
                    }
                    DropdownMenu(expanded = shareMenuExpanded, onDismissRequest = { shareMenuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_menu_link)) },
                            onClick = {
                                shareMenuExpanded = false
                                val shareText = context.getString(
                                    R.string.movie_detail_share_text,
                                    movieForShare?.title.orEmpty(),
                                    "${BuildConfig.SHARE_BASE_URL}/m/$tmdbId"
                                )
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_menu_friend)) },
                            onClick = {
                                shareMenuExpanded = false
                                if (!uiState.isSignedIn) {
                                    Toast.makeText(context, context.getString(R.string.share_friend_signin_toast), Toast.LENGTH_SHORT).show()
                                    onRequireSignIn()
                                } else {
                                    viewModel.loadShareFriends()
                                    showShareWithFriend = true
                                }
                            }
                        )
                    }
                }
                if (showShareWithFriend && movieForShare != null) {
                    ShareWithFriendDialog(
                        movieTitle = movieForShare.title,
                        friends = uiState.shareFriends,
                        isLoadingFriends = uiState.isLoadingShareFriends,
                        isSending = uiState.isSendingShare,
                        onSend = { friendId, note ->
                            viewModel.shareWithFriend(tmdbId, friendId, note) { success ->
                                if (success) showShareWithFriend = false
                                Toast.makeText(
                                    context,
                                    context.getString(if (success) R.string.share_friend_sent else R.string.share_friend_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onDismiss = { showShareWithFriend = false }
                    )
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
                        Spacer(modifier = Modifier.height(10.dp))
                        ExternalLinksRow(tmdbId = movie.tmdbId, imdbId = movie.imdbId)
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

                // UX dorada (oktobar 2026): opis je odmah ispod ocena/dugmadi - ranije je bio tek posle
                // komentara i bioskopskih termina, pa je korisnik morao da prođe kroz njih da bi
                // saznao o čemu je film. Dug opis se skraćuje (isti obrazac kao biografija glumca).
                movie.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    item {
                        var overviewExpanded by remember { mutableStateOf(false) }
                        var overviewOverflows by remember { mutableStateOf(false) }
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.movie_detail_overview_header),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = if (overviewExpanded) Int.MAX_VALUE else OVERVIEW_COLLAPSED_LINES,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { result ->
                                    if (!overviewExpanded) overviewOverflows = result.hasVisualOverflow
                                }
                            )
                            if (overviewOverflows || overviewExpanded) {
                                TextButton(onClick = { overviewExpanded = !overviewExpanded }) {
                                    Text(
                                        stringResource(
                                            if (overviewExpanded) R.string.actor_detail_show_less else R.string.actor_detail_show_more
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // "Istraži više" - trejler pušten u aplikaciji (sekcija se ne prikazuje bez videa).
                val videos = movie.videos.orEmpty()
                if (videos.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.movie_detail_trailer_header),
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                            YouTubeTrailerPlayer(videos = videos)
                        }
                    }
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

                val backdrops = movie.backdrops.orEmpty()
                if (backdrops.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(R.string.movie_detail_images_header),
                            modifier = Modifier.padding(16.dp)
                        )
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(backdrops) { path ->
                                AsyncImage(
                                    model = tmdbPosterUrl(path, "w780"),
                                    contentDescription = stringResource(R.string.movie_detail_backdrop_content_description),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(260.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { fullBackdropPath = path }
                                )
                            }
                        }
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

                // E2.2 dorada (3. septembar 2026) - javni komentari. UX dorada (oktobar 2026): na kraju
                // ekrana, posle informacija o filmu (opis, trejler, bioskopi, slike, glumci).
                item {
                    CommentsSection(
                        tmdbId = tmdbId,
                        uiState = uiState,
                        viewModel = viewModel,
                        onRequireSignIn = onRequireSignIn
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            fullBackdropPath?.let { path ->
                FullScreenImageDialog(
                    imageUrl = tmdbPosterUrl(path, "original") ?: "",
                    onDismiss = { fullBackdropPath = null }
                )
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
