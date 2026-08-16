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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
            val profile = uiState.profile
            if (profile != null && !uiState.isSelf) {
                Box(modifier = Modifier.padding(16.dp)) {
                    AppButton(
                        text = stringResource(R.string.friend_profile_movie_night_cta),
                        onClick = { onNavigateToMovieNight(profile.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                Text(
                                    text = stringResource(R.string.friend_profile_watched_count, profile.watchedMovies.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            }
                        }

                        if (profile.watchedMovies.isEmpty()) {
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
