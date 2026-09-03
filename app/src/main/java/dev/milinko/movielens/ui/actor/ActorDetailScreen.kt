package dev.milinko.movielens.ui.actor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.PersonDetail
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MoviePosterCard
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.ui.theme.MlPosterCornerRadius

// Iznad ovoliko karaktera biografije se prikazuje "Prikaži više" - ispod toga je tekst već
// dovoljno kratak da nema smisla skrivati ga iza 5 linija (videti bioExpanded ispod).
private const val BIOGRAPHY_COLLAPSE_THRESHOLD = 260
private const val BIOGRAPHY_COLLAPSED_MAX_LINES = 5

/**
 * E2.3 (2. septembar 2026) - stranica glumca, otvara se klikom na glumca u "Glumci" traci na
 * Movie Detail ekranu. Foto + ime + odeljenje (known_for_department) na vrhu, kratke činjenice
 * (rođen/preminuo/mesto rođenja - samo ako TMDB ima podatak), biografija (skupljiva ako je duga),
 * pa filmografija kao horizontalna traka postera sa ulogom ispod svakog naslova (MoviePosterCard
 * već ima `caption` parametar za to, videti komponentu).
 */
@Composable
fun ActorDetailScreen(
    personId: Int,
    viewModel: ActorDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToMovie: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.actor_detail_top_bar_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.error_generic_title),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uiState.error ?: stringResource(R.string.error_generic_title))
                        Spacer(modifier = Modifier.height(16.dp))
                        AppButton(
                            text = stringResource(R.string.actor_detail_retry),
                            onClick = { viewModel.loadPerson(personId) }
                        )
                    }
                }

                uiState.person != null -> {
                    ActorDetailContent(
                        person = uiState.person!!,
                        onNavigateToMovie = onNavigateToMovie
                    )
                }
            }
        }
    }
}

@Composable
private fun ActorDetailContent(person: PersonDetail, onNavigateToMovie: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Row(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(160.dp)
                        .clip(RoundedCornerShape(MlPosterCornerRadius))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (person.profilePath != null) {
                        AsyncImage(
                            model = tmdbPosterUrl(person.profilePath, "w342"),
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(MlPosterCornerRadius))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = person.name, style = MaterialTheme.typography.headlineSmall)
                    if (!person.knownForDepartment.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        GenreChip(text = person.knownForDepartment)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!person.birthday.isNullOrBlank()) {
                        ActorFactRow(
                            label = stringResource(R.string.actor_detail_born),
                            value = person.birthday
                        )
                    }
                    if (!person.deathday.isNullOrBlank()) {
                        ActorFactRow(
                            label = stringResource(R.string.actor_detail_died),
                            value = person.deathday
                        )
                    }
                    if (!person.placeOfBirth.isNullOrBlank()) {
                        ActorFactRow(
                            label = stringResource(R.string.actor_detail_place_of_birth),
                            value = person.placeOfBirth
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionHeader(
                    title = stringResource(R.string.actor_detail_biography_header),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (person.biography.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.actor_detail_biography_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    var bioExpanded by remember(person.id) { mutableStateOf(false) }
                    Text(
                        text = person.biography,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (bioExpanded) Int.MAX_VALUE else BIOGRAPHY_COLLAPSED_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (person.biography.length > BIOGRAPHY_COLLAPSE_THRESHOLD) {
                        Text(
                            text = stringResource(
                                if (bioExpanded) R.string.actor_detail_show_less else R.string.actor_detail_show_more
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { bioExpanded = !bioExpanded }
                        )
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                SectionHeader(
                    title = stringResource(R.string.actor_detail_filmography_header),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (person.movieCredits.isEmpty()) {
                    Text(
                        text = stringResource(R.string.actor_detail_filmography_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(person.movieCredits) { credit ->
                            MoviePosterCard(
                                title = credit.title,
                                posterPath = credit.posterPath,
                                caption = credit.character,
                                onClick = { onNavigateToMovie(credit.tmdbId) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActorFactRow(label: String, value: String) {
    Row(modifier = Modifier.padding(bottom = 2.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
