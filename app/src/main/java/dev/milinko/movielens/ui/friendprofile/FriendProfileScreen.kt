package dev.milinko.movielens.ui.friendprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.user.FriendProfileMovie
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.AppButtonVariant
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.RemoteAvatarImage

/**
 * Faza 7 redizajna (MOVIELENS_DESIGN_PLAN.md 2.8). Header (avatar/username/broj filmova) unifikovan
 * sa sopstvenim Profile ekranom - isti obrubljeni krug avatara (accentPrimary border, 80dp). CTA
 * dugme "Predlozi za zajedničko gledanje" premešteno iz skrolujućeg sadržaja u fiksni
 * `Scaffold.bottomBar` (uvek vidljivo, ne zahteva skrol do dna liste odgledanih filmova) i sad je
 * `AppButton` umesto sirovog `Button`. Odgledana lista koristi deljeni `MovieListRow`.
 */
@Composable
fun FriendProfileScreen(
    viewModel: FriendProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToMovieNight: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    // Bugfix (2. septembar 2026) - potvrda pre odbijanja, isti obrazac/stringovi kao
    // ProfileScreen (PendingRequestCard) - dosledna UX na oba mesta gde se zahtev može odbiti.
    var showRejectConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

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
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = uiState.profile?.username ?: stringResource(R.string.friend_profile_default_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.size(48.dp))
            }
        },
        bottomBar = {
            // Bugfix (E1.5, 2. septembar 2026) - RANIJE se do ovog ekrana moglo stići SAMO za već
            // prihvaćene prijatelje, pa je "Movie Night" CTA bilo jedino moguće dugme ovde. Sad se
            // ekran otvara i za NEprihvaćene (ograničen preview) - dugme zavisi od
            // `profile.friendshipStatus`, isti stringovi/varijante kao FriendSearchSection na
            // Profile ekranu (dosledan jezik kroz app).
            val profile = uiState.profile
            if (profile != null && !uiState.isSelf) {
                Box(modifier = Modifier.padding(16.dp)) {
                    // Bugfix (2. septembar 2026) - `friendshipStatus` je nullable jer ga Gson može
                    // vratiti kao null iz starog keš zapisa (videti komentar u UserModels.kt) - String
                    // `when` bez ovog fallback-a ruši aplikaciju (poziva hashCode() na null).
                    when (profile.friendshipStatus ?: "NONE") {
                        "ACCEPTED" -> AppButton(
                            text = stringResource(R.string.friend_profile_movie_night_cta),
                            onClick = { onNavigateToMovieNight(profile.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        "PENDING_SENT" -> AppButton(
                            text = stringResource(R.string.profile_cancel_request),
                            onClick = { viewModel.cancelFriendRequest() },
                            variant = AppButtonVariant.Secondary,
                            enabled = !uiState.isRequestActionInFlight,
                            modifier = Modifier.fillMaxWidth()
                        )
                        "PENDING_RECEIVED" -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Bugfix (2. septembar 2026, korisnički zahtev - "dugme 'Odgovori
                            // ispod' pokazuje nadole ali ispod nema ničega, uzelo je vremena dok
                            // se to pojavi, a pre toga je pisalo 'otkaži zahtev' sto uopste nije
                            // dobro ponasanje") - RANIJE je ovde stajalo samo onemogućeno dugme
                            // koje je upućivalo na prihvatanje/odbijanje "ispod" (taj UI je
                            // postojao SAMO na Profile ekranu, ne i ovde) - sad ekran sam ume da
                            // prihvati/odbije. Odbijanje traži potvrdu (isti dijalog/stringovi
                            // kao PendingRequestCard na Profile ekranu).
                            AppButton(
                                text = stringResource(R.string.profile_reject_content_description),
                                onClick = { showRejectConfirm = true },
                                variant = AppButtonVariant.Secondary,
                                enabled = !uiState.isRequestActionInFlight,
                                modifier = Modifier.weight(1f)
                            )
                            AppButton(
                                text = stringResource(R.string.profile_accept_content_description),
                                onClick = { viewModel.acceptFriendRequest() },
                                variant = AppButtonVariant.Primary,
                                enabled = !uiState.isRequestActionInFlight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        else -> AppButton(
                            text = stringResource(R.string.profile_add_friend),
                            onClick = { viewModel.sendFriendRequest() },
                            variant = AppButtonVariant.Primary,
                            enabled = !uiState.isRequestActionInFlight,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                uiState.isLoading && uiState.profile == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null && uiState.profile == null -> {
                    Text(
                        text = uiState.error ?: stringResource(R.string.error_generic_title),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                uiState.profile != null -> {
                    val profile = uiState.profile!!
                    // E1.5 dorada (2. septembar 2026) - "zaključan" prikaz (bez liste odgledanih
                    // filmova) za svakoga ko NIJE prihvaćen prijatelj, videti FriendsRouting.kt.
                    val isLocked = !uiState.isSelf && profile.friendshipStatus != "ACCEPTED"
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    if (!profile.avatarUrl.isNullOrBlank()) {
                                        RemoteAvatarImage(
                                            url = profile.avatarUrl,
                                            contentDescription = profile.username,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                                        )
                                    } else {
                                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = profile.username,
                                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(profile.username, style = MaterialTheme.typography.titleLarge)
                                // E1.5 dorada - dok nije prihvaćen prijatelj, umesto broja odgledanih
                                // filmova (koji se namerno ne otkriva, videti isLocked) prikazuje se
                                // email, ako backend ima da ga vrati.
                                if (isLocked) {
                                    if (!profile.email.isNullOrBlank()) {
                                        Text(
                                            text = profile.email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.friend_profile_watched_count, profile.watchedMovies.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Faza 7 dorada - prijateljev "mini status", isti izvor podataka
                                // kao sopstveni profil (UserProfile.bio / UserDao.updateProfile).
                                if (!profile.bio.isNullOrBlank()) {
                                    Text(
                                        text = profile.bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, start = 24.dp, end = 24.dp)
                                    )
                                }
                                // E1.5 dorada - presek žanrovskih interesovanja umesto liste filmova
                                // dok prijateljstvo nije prihvaćeno (isti compareTasteProfiles kao
                                // Profile → Prijatelji lista, videti FriendsRouting.kt na backendu).
                                if (isLocked && !profile.commonGenres.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.friend_profile_common_genres_header),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        profile.commonGenres.orEmpty().take(4).forEach { genre ->
                                            GenreChip(text = genre)
                                        }
                                    }
                                }
                            }
                        }

                        if (isLocked) {
                            item {
                                Text(
                                    text = if (profile.friendshipStatus == "PENDING_RECEIVED") {
                                        stringResource(R.string.friend_profile_pending_received_note)
                                    } else {
                                        stringResource(R.string.friend_profile_locked_note)
                                    },
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (profile.watchedMovies.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.friend_profile_empty),
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(profile.watchedMovies, key = { it.tmdbId }) { movie ->
                                WatchedMovieRow(movie = movie, onClick = { onNavigateToDetail(movie.tmdbId) })
                            }
                        }

                        // Prostor na dnu da poslednji red ne bude sakriven ispod fiksnog CTA dugmeta.
                        if (!uiState.isSelf) {
                            item { Spacer(modifier = Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
    }

    // Bugfix (2. septembar 2026) - potvrda pre odbijanja, isti stringovi kao PendingRequestCard
    // na Profile ekranu (dosledna UX na oba mesta gde se zahtev može odbiti).
    if (showRejectConfirm) {
        val username = uiState.profile?.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username)
        AlertDialog(
            onDismissRequest = { showRejectConfirm = false },
            title = { Text(stringResource(R.string.profile_reject_request_dialog_title)) },
            text = { Text(stringResource(R.string.profile_reject_request_dialog_message, username)) },
            confirmButton = {
                TextButton(onClick = {
                    showRejectConfirm = false
                    viewModel.rejectFriendRequest { onNavigateBack() }
                }) {
                    Text(stringResource(R.string.profile_reject_content_description), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRejectConfirm = false }) {
                    Text(stringResource(R.string.profile_remove_cancel))
                }
            }
        )
    }
}

@Composable
private fun WatchedMovieRow(movie: FriendProfileMovie, onClick: () -> Unit) {
    MovieListRow(
        title = movie.title,
        posterPath = movie.posterPath,
        ratingText = movie.rating?.let { "$it/10" },
        // Komentar prijatelja (ako postoji) ima prednost nad "bez ocene" napomenom - korisnička
        // žalba: komentari se nisu prikazivali nigde, ni na profilu prijatelja.
        description = movie.review?.takeIf { it.isNotBlank() }
            ?: if (movie.rating == null) stringResource(R.string.friend_profile_no_rating) else null,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
