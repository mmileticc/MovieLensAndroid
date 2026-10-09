package dev.milinko.movielens.ui.profile

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.util.AppLanguageOption
import dev.milinko.movielens.util.AppLocale
import dev.milinko.movielens.util.DevSettings
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.GenreAffinity
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.ui.auth.AuthUiState
import dev.milinko.movielens.ui.auth.AuthViewModel
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.AppButtonVariant
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.RemoteAvatarImage
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.ui.components.SettingsDialog
import dev.milinko.movielens.ui.components.SignInRequiredState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Content of the Friends page - its own LazyColumn (each page scrolls independently). */
@Composable
internal fun FriendsTabContent(
    uiState: ProfileUiState,
    viewModel: ProfileViewModel,
    onNavigateToFriendProfile: (String) -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onRemoveFriend: (FriendInfo) -> Unit,
    onRejectRequest: (PendingRequest) -> Unit,
    onCancelSentRequest: (id: String, name: String?) -> Unit
) {
    // Swipe-down refresh as a safety net in addition to the automatic refresh after accepting a
    // request (see ProfileViewModel.acceptFriendRequest/refreshFriends).
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshingFriends,
        onRefresh = { viewModel.refreshFriends() },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Incoming requests come FIRST, so typing in the search below never pushes them off screen.
        if (uiState.pendingRequests.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.profile_friend_requests_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(uiState.pendingRequests, key = { "pending-${it.id}" }) { request ->
                PendingRequestCard(
                    request = request,
                    onAccept = { viewModel.acceptFriendRequest(request.id) },
                    onRejectClick = { onRejectRequest(request) },
                    onOpenProfile = { onNavigateToFriendProfile(request.id) }
                )
            }
        }

        item {
            FriendSearchSection(
                query = uiState.friendSearchQuery,
                isSearching = uiState.isSearchingFriends,
                error = uiState.friendSearchError,
                results = uiState.friendSearchResults,
                inFlightIds = uiState.friendActionInFlightIds,
                onQueryChange = viewModel::setFriendSearchQuery,
                onAdd = viewModel::sendFriendRequest,
                onAccept = viewModel::acceptFriendRequest,
                onCancelRequest = onCancelSentRequest,
                onOpenProfile = onNavigateToFriendProfile
            )
        }

        // Sent requests in their own section (like friends and incoming requests), so their status
        // is visible without searching for the person again.
        if (uiState.sentRequests.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.profile_sent_requests_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(uiState.sentRequests, key = { "sent-${it.id}" }) { request ->
                SentRequestCard(
                    request = request,
                    onCancelClick = { onCancelSentRequest(request.id, request.username) },
                    onOpenProfile = { onNavigateToFriendProfile(request.id) }
                )
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.profile_friends_header),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        if (uiState.isLoading && uiState.friends.isEmpty()) {
            // Until the first call returns, "you have no friends" is not shown - it would be false
            // if the list simply has not loaded yet (e.g. a quick swipe to this tab).
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (uiState.friends.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.profile_friends_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(uiState.friends, key = { it.friendId }) { friend ->
                FriendCard(
                    friend,
                    onClick = { onNavigateToFriendProfile(friend.friendId) },
                    onRemove = { onRemoveFriend(friend) }
                )
            }
        }

        if (uiState.friendActivity.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.profile_friend_activity_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(uiState.friendActivity, key = { it.activityId }) { activity ->
                FriendActivityRow(activity, onClick = { onNavigateToDetail(activity.tmdbId) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

/** Friend card: avatar, name, taste match % and shared genres as small chips. */
@Composable
internal fun FriendCard(
    friend: FriendInfo,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape)) {
                if (!friend.avatar.isNullOrBlank()) {
                    RemoteAvatarImage(
                        url = friend.avatar,
                        contentDescription = friend.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = friend.username,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(friend.username, style = MaterialTheme.typography.titleSmall)
                // Shown only when the backend computed a match (it is 0 when there is not enough
                // rating data).
                if (friend.matchScore > 0f) {
                    Text(
                        text = stringResource(R.string.profile_match_percent, friend.matchScore),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (friend.commonGenres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        friend.commonGenres.take(2).forEach { genre ->
                            GenreChip(text = genre)
                        }
                    }
                }
            }
            // "Remove" is behind the overflow menu (⋮) so it is not hit by accident next to the
            // clickable card; it still asks for confirmation.
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.profile_friend_options_content_description, friend.username)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_remove_friend_menu), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

/**
 * User search for adding friends (search as you type) with the results and their friendship
 * actions.
 */
@Composable
internal fun FriendSearchSection(
    query: String,
    isSearching: Boolean,
    error: String?,
    results: List<UserSearchResult>,
    inFlightIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onAccept: (String) -> Unit,
    onCancelRequest: (id: String, name: String?) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_search_users_label)) },
            singleLine = true,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        )

        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                results.forEach { result ->
                    UserSearchResultRow(
                        result = result,
                        isActionInFlight = result.id in inFlightIds,
                        onAdd = { onAdd(result.id) },
                        onAccept = { onAccept(result.id) },
                        onCancelRequest = { onCancelRequest(result.id, result.username) },
                        // Always clickable: FriendProfileScreen shows a limited preview for any
                        // friendship status.
                        onClick = { onOpenProfile(result.id) }
                    )
                }
            }
        } else if (error != null && !isSearching) {
            // A network failure must not look like "no users with that name".
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else if (!isSearching && query.trim().length >= 2) {
            Text(
                text = stringResource(R.string.profile_search_no_results, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** One search result: avatar, name and the action button matching the friendship status. */
@Composable
internal fun UserSearchResultRow(
    result: UserSearchResult,
    isActionInFlight: Boolean,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
    onCancelRequest: () -> Unit,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            // The button is disabled while an action for this id is in flight (see
            // friendActionInFlightIds in ProfileViewModel), so rapid add/cancel taps cannot
            // overlap.
            when (result.status) {
                "NONE" -> AppButton(text = stringResource(R.string.profile_add_friend), onClick = onAdd, variant = AppButtonVariant.Primary, enabled = !isActionInFlight)
                "PENDING_SENT" -> TextButton(onClick = onCancelRequest, enabled = !isActionInFlight) {
                    Text(stringResource(R.string.profile_cancel_request), color = MaterialTheme.colorScheme.error)
                }
                // A real "Accept" button; rejecting goes through the profile or the request card at
                // the top of the tab, where a confirmation exists.
                "PENDING_RECEIVED" -> AppButton(text = stringResource(R.string.profile_accept_request), onClick = onAccept, variant = AppButtonVariant.Primary, enabled = !isActionInFlight)
                "ACCEPTED" -> AppButton(text = stringResource(R.string.profile_already_friends), onClick = {}, variant = AppButtonVariant.Secondary, enabled = false)
                else -> AppButton(text = stringResource(R.string.profile_add_friend), onClick = onAdd, variant = AppButtonVariant.Primary, enabled = !isActionInFlight)
            }
        }
    }
}

/**
 * One activity feed entry, using the shared MovieListRow: the movie (poster + title) is the main
 * element and "who did what" is the description below. Tapping opens the movie.
 */
@Composable
internal fun FriendActivityRow(activity: FriendActivity, onClick: () -> Unit) {
    // The rating is shown only for "rated" entries.
    val rating = activity.metadata.rating?.takeIf { activity.action == "rated" }
    val metaText = if (rating != null) {
        stringResource(R.string.profile_activity_row_rated, activity.friendName, rating.toInt())
    } else {
        stringResource(R.string.profile_activity_row_watched, activity.friendName)
    }

    MovieListRow(
        title = activity.movieTitle,
        posterPath = activity.posterPath,
        metaText = metaText,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
