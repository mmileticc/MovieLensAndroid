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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.user.FriendProfileMovie
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.AppButtonVariant
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.RemoteAvatarImage

/**
 * Another user's profile: avatar, name, status line and either the watched movies (accepted friend)
 * or a limited preview with shared genres (not a friend yet). The friendship action (add / cancel /
 * accept / reject) or the "Movie Night" button sits in a fixed bottom bar, always visible.
 */
@Composable
fun FriendProfileScreen(
    viewModel: FriendProfileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToMovieNight: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Confirmation before rejecting, same dialog and strings as PendingRequestCard on Profile.
    var showRejectConfirm by remember { mutableStateOf(false) }
    // Cancelling a sent request - same confirmation as on the Profile screen.
    var showCancelConfirm by remember { mutableStateOf(false) }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            // The screen opens for non-friends too (limited preview), so the button depends on
            // `profile.friendshipStatus`, with the same strings/variants as FriendSearchSection.
            val profile = uiState.profile
            if (profile != null && !uiState.isSelf) {
                Box(modifier = Modifier.padding(16.dp)) {
                    // friendshipStatus can be null from an old cache entry (see UserModels.kt); a
                    // String `when` without this fallback would crash (hashCode() on null).
                    when (profile.friendshipStatus ?: "NONE") {
                        "ACCEPTED" -> AppButton(
                            text = stringResource(R.string.friend_profile_movie_night_cta),
                            onClick = { onNavigateToMovieNight(profile.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        "PENDING_SENT" -> AppButton(
                            text = stringResource(R.string.profile_cancel_request),
                            onClick = { showCancelConfirm = true },
                            variant = AppButtonVariant.Secondary,
                            enabled = !uiState.isRequestActionInFlight,
                            modifier = Modifier.fillMaxWidth()
                        )
                        "PENDING_RECEIVED" -> Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // An incoming request can be accepted or rejected right here; rejecting
                            // asks for confirmation (same dialog/strings as PendingRequestCard).
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
                    ErrorCard(
                        message = uiState.error ?: stringResource(R.string.error_generic_title),
                        onRetry = viewModel::loadProfile,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }
                uiState.profile != null -> {
                    val profile = uiState.profile!!
                    // "Locked" view (no watched list) for anyone who is not an accepted friend
                    // (see FriendsRouting.kt on the backend).
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
                                // Locked: the number of watched movies is not revealed; the email is
                                // shown only if the backend returns it (it does not for non-friends).
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
                                // The friend's status line, same data as one's own profile
                                // (UserProfile.bio).
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
                                // Shared genre interests instead of the movie list until the friendship
                                // is accepted (same compareTasteProfiles as the Friends list, see
                                // FriendsRouting.kt on the backend).
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

                    // Space at the bottom so the last row is not hidden under the fixed button.
                        if (!uiState.isSelf) {
                            item { Spacer(modifier = Modifier.height(72.dp)) }
                        }
                    }
                }
            }
        }
    }

    if (showCancelConfirm) {
        val username = uiState.profile?.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username)
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(stringResource(R.string.profile_cancel_request_dialog_title)) },
            text = { Text(stringResource(R.string.profile_cancel_request_dialog_message, username)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    viewModel.cancelFriendRequest()
                }) {
                    Text(stringResource(R.string.profile_cancel_request), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text(stringResource(R.string.profile_remove_cancel))
                }
            }
        )
    }

    // Confirmation before rejecting (same strings as PendingRequestCard on Profile).
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
        // "No rating" note when the friend watched the movie without rating it.
        description = if (movie.rating == null) stringResource(R.string.friend_profile_no_rating) else null,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
