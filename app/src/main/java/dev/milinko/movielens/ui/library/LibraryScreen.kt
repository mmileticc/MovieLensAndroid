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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.RatingStars

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
        viewModel.loadLibrary()
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
            AppBottomNavigationBar(
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
            if (uiState.requiresSignIn) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Uloguj se da vidiš svoju biblioteku")
                }
            } else {
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
                                        onRemoveClick = { viewModel.removeFromWatchlist(item.tmdbId) }
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
                                items(uiState.history, key = { it.tmdbId }) { item ->
                                    HistoryItemCard(
                                        item = item,
                                        onMovieClick = { onNavigateToDetail(item.tmdbId) },
                                        onRate = { stars -> viewModel.rateMovie(item.tmdbId, stars) }
                                    )
                                }
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
    onMovieClick: () -> Unit,
    onRate: (Int) -> Unit
) {
    // Tap na ocenu otvara/zatvara red od 10 zvezdica za brzu izmenu, bez odlaska na Movie Detail.
    var isEditingRating by remember { mutableStateOf(false) }

    Column {
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
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable { isEditingRating = !isEditingRating }
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFFF5C518)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${String.format("%.1f", item.userRating)}/10", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isEditingRating) "Zatvori" else "Izmeni",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isEditingRating) {
            RatingStars(
                rating = item.userRating.toInt(),
                onRate = { stars ->
                    onRate(stars)
                    isEditingRating = false
                },
                modifier = Modifier.padding(start = 84.dp, bottom = 8.dp)
            )
        }
    }
}

// Zajednička AppBottomNavigationBar komponenta (ui/components) — bez lokalnih duplikata.

