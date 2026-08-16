package dev.milinko.movielens.ui.movienight

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.theme.MlPosterCornerRadius

/**
 * Faza 7 redizajna (MOVIELENS_DESIGN_PLAN.md 2.7). Dokument doslovno traži da
 * `MovieSuggestionRow` postane `MoviePosterCard` - namerno odstupanje: `MoviePosterCard` je
 * vertikalna kartica (110x150 poster + naslov ispod), napravljena za horizontalne redove
 * (Home). Ovaj ekran je skrolujuća VERTIKALNA lista predloga sa dodatnim tekstom
 * (matchReason, dva match-procenta) koji ne staje ispod uskog vertikalnog postera bez da
 * kartica postane nezgrapno visoka. Zadržan je red-layout (poster levo, tekst desno), a
 * "lepši prikaz" iz dokumenta je ispunjen zamenom plain teksta "Ti: X% • Prijatelj: Y%" pravim
 * dvobojnim mini trakama napretka (accentPrimary za "Ti", accentSecondary za "Prijatelj").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieNightScreen(
    viewModel: MovieNightViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSuggestions()
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
                    text = stringResource(R.string.movie_night_top_bar_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.isRefreshing) {
                    // Rotirajuća refresh ikonica dok se predlozi osvežavaju - vizuelna potvrda
                    // da je akcija u toku, umesto da ikonica samo nestane i vrati se.
                    val infiniteTransition = rememberInfiniteTransition(label = "refresh_rotation")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, easing = LinearEasing)
                        ),
                        label = "refresh_rotation_value"
                    )
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.movie_night_refresh_content_description),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotation)
                    )
                } else {
                    IconButton(onClick = { viewModel.refreshSuggestions() }, enabled = !uiState.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.movie_night_refresh_content_description))
                    }
                }
            }
        }
    ) { paddingValues ->
        // Swipe-down radi isto što i "Osveži predloge" dugme u top bar-u (rotira na sledeću TMDB
        // stranicu, videti MovieNightViewModel.refreshSuggestions) - ista isRefreshing zastavica
        // pokreće oba, samo dva različita "okidača" iste akcije.
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshSuggestions() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.error != null -> {
                        Text(
                            text = uiState.error ?: stringResource(R.string.movie_night_error_generic),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    uiState.suggestions.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.movie_night_empty_state),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.suggestions, key = { it.tmdbId }) { suggestion ->
                                MovieSuggestionRow(suggestion = suggestion, onClick = { onNavigateToDetail(suggestion.tmdbId) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieSuggestionRow(suggestion: MovieSuggestion, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(MlPosterCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (suggestion.posterPath != null) {
                    // Lesson learned od Faze 2 (MoviePosterCard/MovieListRow bug) - AsyncImage mora
                    // imati fillMaxSize() PRE clip() unutar sized Box-a, inače se renderuje sitno
                    // i pinovano gore levo.
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(tmdbPosterUrl(suggestion.posterPath, "w92"))
                            .crossfade(true)
                            .build(),
                        contentDescription = suggestion.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(MlPosterCornerRadius))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = suggestion.matchReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                MatchBar(
                    label = stringResource(R.string.movie_night_your_match_label),
                    percent = suggestion.scores.yourMatch,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                MatchBar(
                    label = stringResource(R.string.movie_night_friend_match_label),
                    percent = suggestion.scores.theirMatch,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/** Mini traka napretka za jedan match-procenat (Ti/Prijatelj) - zamenjuje plain "%1$d%%" tekst. */
@Composable
private fun MatchBar(label: String, percent: Float, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp)
        )
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(MaterialTheme.shapes.small),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = "${percent.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
