package dev.milinko.movielens.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
fun MovieDetailScreen(
    tmdbId: Int,
    viewModel: MovieDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(tmdbId) {
        viewModel.loadMovieDetail(tmdbId)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onNavigateBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Film detalji",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(48.dp))
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
                Text("Greška", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(uiState.error!!)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.loadMovieDetail(tmdbId) }) {
                    Text("Pokušaj ponovno")
                }
            }
        } else if (uiState.movieDetail != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                item {
                    val movie = uiState.movieDetail!!
                    if (movie.posterPath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(tmdbPosterUrl(movie.posterPath))
                                .crossfade(true)
                                .build(),
                            contentDescription = movie.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    val movie = uiState.movieDetail!!
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            movie.title,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                movie.releaseDate?.take(4) ?: "",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${movie.runtime} min",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFFF5C518)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${String.format("%.1f", movie.voteAverage)}/10", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row {
                            Button(
                                onClick = { /* TODO */ },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Watchlist")
                            }
                        }
                    }
                }

                item {
                    val movie = uiState.movieDetail!!
                    if (movie.genres.isNotEmpty()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Žanrovi",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                movie.genres.take(3).forEach { genre ->
                                    ElevatedCard {
                                        Text(genre.name, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val movie = uiState.movieDetail!!
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Opis",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        movie.overview?.let { Text(it, fontSize = 14.sp) }
                    }
                }

                if (uiState.movieDetail!!.cast.isNotEmpty()) {
                    item {
                        Text(
                            "Glumci",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(uiState.movieDetail!!.cast.take(6)) { actor ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(actor.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                actor.character ?: "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

