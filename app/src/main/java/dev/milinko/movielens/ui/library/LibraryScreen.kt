package dev.milinko.movielens.ui.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadLibrary("user123") // TODO: Get actual userId from auth
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Biblioteka",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(
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
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Watchlist") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Istorija") }
                )
            }

            when (selectedTabIndex) {
                0 -> {
                    if (uiState.watchlist.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nema filmova u watchlist-u")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.watchlist) { item ->
                                WatchlistItemCard(
                                    item = item,
                                    onMovieClick = { onNavigateToDetail(item.tmdbId) },
                                    onRemoveClick = { viewModel.removeFromWatchlist("user123", item.tmdbId) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (uiState.history.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nema istorije")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.history) { item ->
                                HistoryItemCard(
                                    item = item,
                                    onMovieClick = { onNavigateToDetail(item.tmdbId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistItemCard(
    item: dev.milinko.movielens.data.remote.movie.WatchlistItem,
    onMovieClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMovieClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (item.posterPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(tmdbPosterUrl(item.posterPath, "w92"))
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Text(item.title, fontWeight = FontWeight.SemiBold)
            if (item.priority > 0) {
                Text(
                    "Prioritet: ${item.priority}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onRemoveClick) {
            androidx.compose.material3.Icon(
                Icons.Default.Delete,
                contentDescription = "Remove",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: dev.milinko.movielens.data.remote.movie.HistoryItem,
    onMovieClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onMovieClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (item.posterPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(tmdbPosterUrl(item.posterPath, "w92"))
                    .crossfade(true)
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically)
        ) {
            Text(item.title, fontWeight = FontWeight.SemiBold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFF5C518)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("${String.format("%.1f", item.userRating)}/10", fontSize = 12.sp)
            }
        }
    }
}

enum class BottomNavTab {
    HOME, SEARCH, LIBRARY, PROFILE
}

@Composable
fun BottomNavigationBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem("Home", Icons.Default.Search, selectedTab == BottomNavTab.HOME) {
                onTabSelected(BottomNavTab.HOME)
            }
            BottomNavItem("Pretraga", Icons.Default.Search, selectedTab == BottomNavTab.SEARCH) {
                onTabSelected(BottomNavTab.SEARCH)
            }
            BottomNavItem("Biblioteka",
                Icons.AutoMirrored.Filled.LibraryBooks, selectedTab == BottomNavTab.LIBRARY) {
                onTabSelected(BottomNavTab.LIBRARY)
            }
            BottomNavItem("Profil", Icons.Default.Settings, selectedTab == BottomNavTab.PROFILE) {
                onTabSelected(BottomNavTab.PROFILE)
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

