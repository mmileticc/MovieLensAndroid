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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.MoviePosterCard
import dev.milinko.movielens.ui.components.MovieneeWordmark
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.ui.notifications.NotificationsViewModel
import kotlinx.coroutines.launch

/**
 * Home: the bell for notifications, then rows "Suggested for you", "In cinemas now", "Continue
 * watching" (watchlist) and "Friends are watching", followed by the infinitely scrolling popular
 * movies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFriends: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSuggested: () -> Unit,
    onNavigateToNowInCinemas: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // If popular movies failed to load at app start (e.g. the backend was cold), this is the only
    // chance to retry: HomeViewModel lives for the whole session, so its init{} runs only once.
    LaunchedEffect(Unit) {
        viewModel.ensureTrendingLoaded()
    }

    Scaffold(
        floatingActionButton = {
            // "Back to top" button, shown only after scrolling down far enough. A small
            // surfaceVariant FAB, because it is a secondary action (red is reserved for main actions).
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
                        BottomNavTab.FRIENDS -> onNavigateToFriends()
                        BottomNavTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        }
    ) { paddingValues ->
        // Infinite scroll: when the last visible item is near the end, request the next TMDB page.
        // derivedStateOf so this is recomputed only when the last visible index really changes.
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

        // Swipe down to refresh (see HomeViewModel.refreshAll).
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
                HomeHeader(onNotificationsClick = onNavigateToNotifications)
            }

            // Each row is in an ElevatedCard, like the Profile and Library screens. "Popular" stays
            // outside a card (a plain list like the Library lists) because it is the long, infinitely
            // scrolling main content - it is separated by a HorizontalDivider instead of one card
            // that grows forever.
            if (uiState.personalizedMovies.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.home_section_personalized),
                                actionText = stringResource(R.string.home_see_all),
                                onActionClick = onNavigateToSuggested,
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
                                actionText = stringResource(R.string.home_see_all),
                                onActionClick = onNavigateToNowInCinemas,
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

            if (uiState.friendsActivity.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.home_section_friends_watching),
                                actionText = stringResource(R.string.home_see_all),
                                onActionClick = onNavigateToFriends,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.friendsActivity, key = { it.activityId }) { activity ->
                                    FriendActivityCard(activity = activity, onClick = { onNavigateToDetail(activity.tmdbId) })
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
                // First load / cold start - a spinner instead of an empty screen until the first
                // page arrives.
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
}

@Composable
private fun HomeHeader(
    onNotificationsClick: () -> Unit,
    notificationsViewModel: NotificationsViewModel = hiltViewModel()
) {
    val notifications by notificationsViewModel.uiState.collectAsStateWithLifecycle()
    // Reloaded on every return to Home (e.g. after the Notifications screen), so the count is
    // always right.
    LaunchedEffect(Unit) { notificationsViewModel.load() }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) { HomeTitle() }
        // Guests have no notifications - no bell.
        if (!notifications.requiresSignIn) {
            IconButton(onClick = onNotificationsClick) {
                BadgedBox(
                    badge = {
                        if (notifications.unreadCount > 0) {
                            Badge { Text(if (notifications.unreadCount > 9) "9+" else notifications.unreadCount.toString()) }
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.notifications_title)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeTitle() {
    Column {
        // Two-color "movie-nee" wordmark (see MovieneeWordmark.kt) in the theme's titleLarge.
        MovieneeWordmark(style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.home_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Thin wrappers that map each model to the plain parameters of the shared MoviePosterCard /
// MovieListRow (the app has no single movie model).
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
private fun FriendActivityCard(activity: FriendActivity, onClick: () -> Unit) {
    val rating = activity.metadata.rating
    MoviePosterCard(
        title = activity.movieTitle,
        posterPath = activity.posterPath,
        caption = if (rating != null) "${activity.friendName} ★ ${rating.toInt()}" else activity.friendName,
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



