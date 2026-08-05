package dev.milinko.movielens.ui.home
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
// ...existing imports...
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
// ...existing code...
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
// ...existing code...

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToSearch() },
                containerColor = Color(0xFFE50914),
                contentColor = Color.White,
                modifier = Modifier
                    .padding(16.dp)
                    .size(56.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
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
                        BottomNavTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                HomeHeader()
            }

            // Search / scan lives on the Search screen. Home keeps a quick FAB for search.

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Popularni filmovi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(uiState.trendingMovies.take(5)) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onNavigateToDetail(movie.tmdbId) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Home only shows trending items and keeps FAB to quick-open Search screen.
}


// ...existing code...

@Composable
private fun HomeHeader() {
    Column {
        Text(
            "MovieLens",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Skeni, pretraži i pronađi filmove",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MovieCard(
    movie: dev.milinko.movielens.data.remote.movie.MovieSearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (movie.posterPath != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(tmdbPosterUrl(movie.posterPath, "w92"))
                    .crossfade(true)
                    .build(),
                contentDescription = movie.title,
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
            Text(movie.title, fontWeight = FontWeight.SemiBold)
            Text(movie.releaseDate?.take(4) ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFF5C518))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}/10", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Greška", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, fontSize = 13.sp)
        }
    }
}



