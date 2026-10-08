package dev.milinko.movielens.ui.actor

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.PersonDetail
import dev.milinko.movielens.data.remote.movie.PersonMovieCredit
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.FullScreenImageDialog
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MoviePosterCard
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.util.AppLocale
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val BIOGRAPHY_COLLAPSED_MAX_LINES = 5

/** Sortiranje filmografije glumca. */
private enum class FilmographySort(@StringRes val labelRes: Int) {
    POPULARITY(R.string.actor_sort_popularity),
    NEWEST(R.string.actor_sort_newest),
    OLDEST(R.string.actor_sort_oldest),
    RATING(R.string.actor_sort_rating)
}

// TMDB šalje datume kao "yyyy-MM-dd". SimpleDateFormat (ne java.time) jer je minSdk 24, a
// java.time bez desugaring-a traži API 26.
private fun parseTmdbDate(value: String): Date? = try {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)
} catch (e: ParseException) {
    null
}

/**
 * "1974-11-11" -> "11. novembra 1974." / "November 11, 1974" po jeziku aplikacije (ako parsiranje
 * ne uspe, vraća originalni tekst).
 */
private fun formatTmdbDate(value: String): String {
    val date = parseTmdbDate(value) ?: return value
    val format = if (AppLocale.currentTag() == "en") {
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    } else {
        SimpleDateFormat("d. MMMM yyyy.", Locale.forLanguageTag("sr-Latn"))
    }
    return format.format(date)
}

/** Pun broj godina između rođenja i [toDate] (ili danas). */
private fun ageInYears(birthday: String, toDate: String?): Int? {
    val birth = parseTmdbDate(birthday) ?: return null
    val end = toDate?.let { parseTmdbDate(it) ?: return null } ?: Date()
    val b = Calendar.getInstance().apply { time = birth }
    val e = Calendar.getInstance().apply { time = end }
    var age = e.get(Calendar.YEAR) - b.get(Calendar.YEAR)
    if (e.get(Calendar.DAY_OF_YEAR) < b.get(Calendar.DAY_OF_YEAR)) age--
    return age.takeIf { it >= 0 }
}

/**
 * E2.3 (2. septembar 2026, dorada vizuelnog uokvirenja posle prvog testiranja - "malo lepse
 * uokviriti prikazati") - stranica glumca, otvara se klikom na glumca u "Glumci" traci na Movie
 * Detail ekranu. Foto glumca je sad pun "hero" preko celog ekrana (isti obrazac kao Movie Detail
 * poster hero - gradijent "scrim" preko donje polovine + ime/odeljenje ispisani preko slike),
 * umesto ranije male kvadratne slike pored teksta - deluje kao prava naslovna stranica, ne kao
 * red podataka. Kratke činjenice (rođen/preminuo/mesto rođenja) su sad u zasebnoj `ElevatedCard`
 * ispod hero-a (isti "info kartica" obrazac kao `CinemaShowtimeCard`/`FriendsWatchedBadge` na
 * Movie Detail ekranu) umesto golog teksta - jasnije uokvireno, lakše se skenira pogledom.
 *
 * Dorada (2. septembar 2026, korisnički bug report - "biografija se na tamnoj temi ne vidi jer su
 * crna slova na tamnu pozadinu") - ekran je RANIJE bio ručno sklopljen preko golog `Box`/`Column`
 * (bez `Scaffold`), za razliku od svih ostalih ekrana u app-u (`MovieDetailScreen`/
 * `FriendProfileScreen`/itd. svi koriste `Scaffold`). `Scaffold` iznutra umotava sadržaj u
 * `Surface` koja postavlja `LocalContentColor` na ispravnu, temi-svesnu boju (`onBackground` u
 * tamnoj temi, ne crna) - bez nje, svaki `Text` BEZ eksplicitno navedene `color` (biografija,
 * naslov trake, vrednosti u `ActorFactRow`) pada na globalni Compose default `Color.Black`, koji
 * je nevidljiv na tamnoj pozadini (u svetloj temi je isti bag bio nevidljiv jer se crna na belom
 * vidi normalno). Fix: ceo ekran sad koristi `Scaffold(topBar = {...})`, isti obrazac kao
 * `MovieDetailScreen`.
 *
 * Dorada (2. septembar 2026, korisnički zahtev - "hocu da slika glumca moze da se otvori kao i
 * poster slika") - fotografija u hero-u je sad klikabilna i otvara `FullScreenImageDialog`
 * (deljena komponenta izdvojena iz `MovieDetailScreen`-ovog ranije privatnog
 * `FullScreenPosterDialog`-a - identičan pinch-to-zoom/pan mehanizam kao pregled postera filma).
 */
@Composable
fun ActorDetailScreen(
    personId: Int,
    viewModel: ActorDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToMovie: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(personId) {
        viewModel.loadPerson(personId)
    }

    Scaffold(
        topBar = {
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
        }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
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
                    paddingValues = paddingValues,
                    onNavigateToMovie = onNavigateToMovie
                )
            }
        }
    }
}

@Composable
private fun ActorDetailContent(
    person: PersonDetail,
    paddingValues: PaddingValues,
    onNavigateToMovie: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
    ) {
        item { ActorHero(person = person) }

        item { ActorQuickFacts(person = person) }

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
                    // UX dorada (oktobar 2026): "Prikaži više" se pojavljuje kad se tekst STVARNO
                    // skrati (hasVisualOverflow), a ne po broju karaktera - na uskim ekranima je
                    // i biografija kraća od praga bila odsečena bez načina da se otvori.
                    var bioOverflows by remember(person.id) { mutableStateOf(false) }
                    Text(
                        text = person.biography,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (bioExpanded) Int.MAX_VALUE else BIOGRAPHY_COLLAPSED_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (!bioExpanded) bioOverflows = result.hasVisualOverflow
                        }
                    )
                    if (bioOverflows || bioExpanded) {
                        TextButton(onClick = { bioExpanded = !bioExpanded }) {
                            Text(
                                stringResource(
                                    if (bioExpanded) R.string.actor_detail_show_less else R.string.actor_detail_show_more
                                )
                            )
                        }
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
                    // UX dorada (oktobar 2026): sortiranje filmografije. Podrazumevano ostaje
                    // popularnost (redosled koji backend već vraća - "najpoznatije uloge prve").
                    var sort by remember(person.id) { mutableStateOf(FilmographySort.POPULARITY) }
                    val sortedCredits = remember(person.movieCredits, sort) {
                        when (sort) {
                            FilmographySort.POPULARITY -> person.movieCredits
                            // Filmovi bez datuma (najavljeni bez termina) idu na kraj u oba smera.
                            FilmographySort.NEWEST -> person.movieCredits.sortedWith(
                                compareBy<PersonMovieCredit> { it.releaseDate.isNullOrBlank() }.thenByDescending { it.releaseDate }
                            )
                            FilmographySort.OLDEST -> person.movieCredits.sortedWith(
                                compareBy<PersonMovieCredit> { it.releaseDate.isNullOrBlank() }.thenBy { it.releaseDate }
                            )
                            FilmographySort.RATING -> person.movieCredits.sortedByDescending { it.voteAverage ?: 0.0 }
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        items(FilmographySort.entries) { option ->
                            FilterChip(
                                selected = sort == option,
                                onClick = { sort = option },
                                label = { Text(stringResource(option.labelRes)) }
                            )
                        }
                    }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(sortedCredits, key = { it.tmdbId }) { credit ->
                            MoviePosterCard(
                                title = credit.title,
                                posterPath = credit.posterPath,
                                // "2019 · Joker" - godina pomaže da se snađe u dugoj karijeri.
                                caption = listOfNotNull(
                                    credit.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                                    credit.character?.takeIf { it.isNotBlank() }
                                ).joinToString(" · ").ifBlank { null },
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

/**
 * Pun "hero" preko celog ekrana - isti obrazac kao poster/naslov na Movie Detail ekranu (Faza 5
 * redizajna): fotografija fillMaxSize, "scrim" gradijent koji od pola visine naniže prelazi u
 * pozadinsku boju radi čitljivosti teksta, ime/odeljenje ispisani preko donjeg dela slike. Bez
 * fotografije (TMDB je nema za svakog glumca/člana ekipe) prikazuje se siluetna ikonica umesto
 * praznog kvadrata - jasno komunicira "nema slike" umesto da deluje kao da nešto nije učitano.
 */
@Composable
private fun ActorHero(person: PersonDetail) {
    var showFullPhoto by remember(person.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        if (person.profilePath != null) {
            AsyncImage(
                model = tmdbPosterUrl(person.profilePath, "w500"),
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showFullPhoto = true }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // Frakcioni color stop-ovi (ne raw piksel startY) - isti obrazac kao Movie Detail hero,
        // nezavisno od gustine ekrana "scrim" uvek počinje od pola visine hero-a naniže.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!person.knownForDepartment.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                GenreChip(text = person.knownForDepartment)
            }
        }
    }

    if (showFullPhoto && person.profilePath != null) {
        FullScreenImageDialog(
            imageUrl = tmdbPosterUrl(person.profilePath, "w500") ?: "",
            onDismiss = { showFullPhoto = false }
        )
    }
}

/**
 * Kratke činjenice (rođen/preminuo/mesto rođenja) u zasebnoj kartici ispod hero-a - isti
 * "info kartica" obrazac kao `CinemaShowtimeCard`/`FriendsWatchedBadge` na Movie Detail ekranu,
 * umesto ranijeg golog teksta. Ceo blok se ne prikazuje ako TMDB nema nijedan od ova tri podatka
 * (česta pojava za manje poznate glumce) - nema smisla prikazati praznu karticu.
 */
@Composable
private fun ActorQuickFacts(person: PersonDetail) {
    val hasFacts = !person.birthday.isNullOrBlank() ||
        !person.deathday.isNullOrBlank() ||
        !person.placeOfBirth.isNullOrBlank()
    if (!hasFacts) return

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!person.birthday.isNullOrBlank()) {
                val born = formatTmdbDate(person.birthday)
                // Uzrast se prikazuje samo za žive (za preminule ide uz datum smrti ispod).
                val age = if (person.deathday.isNullOrBlank()) ageInYears(person.birthday, null) else null
                ActorFactRow(
                    label = stringResource(R.string.actor_detail_born),
                    value = age?.let { stringResource(R.string.actor_detail_date_with_age, born, it) } ?: born
                )
            }
            if (!person.deathday.isNullOrBlank()) {
                val died = formatTmdbDate(person.deathday)
                val ageAtDeath = person.birthday?.let { ageInYears(it, person.deathday) }
                ActorFactRow(
                    label = stringResource(R.string.actor_detail_died),
                    value = ageAtDeath?.let { stringResource(R.string.actor_detail_date_with_age, died, it) } ?: died
                )
            }
            if (!person.placeOfBirth.isNullOrBlank()) {
                ActorFactRow(
                    label = stringResource(R.string.actor_detail_place_of_birth),
                    value = person.placeOfBirth,
                    showLocationIcon = true
                )
            }
        }
    }
}

@Composable
private fun ActorFactRow(label: String, value: String, showLocationIcon: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showLocationIcon) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
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
