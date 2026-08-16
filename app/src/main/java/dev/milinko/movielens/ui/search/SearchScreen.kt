package dev.milinko.movielens.ui.search

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.vision.ScrapedMovie
import dev.milinko.movielens.data.remote.vision.TmdbMatch
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.auth.AuthViewModel
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.PremiumBadge
import dev.milinko.movielens.ui.components.SectionHeader
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onRequireSignIn: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val authViewModel: AuthViewModel = hiltViewModel()
    val context = LocalContext.current
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    fun requireScanAuthOrProceed(onAuthorized: () -> Unit) {
        if (authViewModel.isAlreadySignedIn()) {
            onAuthorized()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.search_signin_required_toast),
                Toast.LENGTH_LONG
            ).show()
            onRequireSignIn()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            viewModel.onImageSelected(uri?.toString())
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                viewModel.onImageSelected(cameraImageUri?.toString())
            }
        }
    )

    fun openCamera() {
        try {
            val directory = File(context.cacheDir, "images")
            if (!directory.exists()) directory.mkdirs()
            val tempFile = File(directory, "camera_image_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, tempFile)
            cameraImageUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Log.e("SearchScreen", "Error opening camera", e)
            Toast.makeText(context, context.getString(R.string.search_camera_error, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) openCamera()
        }
    )

    Scaffold(
        bottomBar = {
            // Faza 6 redizajna (videti MOVIELENS_DESIGN_PLAN.md 2.3): polje za pretragu ide na
            // dno, ne na vrh - dohvatljivije palcem, prati tastaturu naviše kad je fokusirano.
            // imePadding() ide na CEO donji klaster (pregled slike + input bar + app-wide nav) jer
            // Scaffold ima samo jedan bottomBar slot - ceo klaster se diže zajedno iznad tastature,
            // ne samo input bar (manja, prihvatljiva razlika od "samo input bar" iz dokumenta).
            Column(modifier = Modifier.imePadding()) {
                if (uiState.selectedImageUri != null) {
                    SelectedImagePreviewRow(
                        imageUri = uiState.selectedImageUri!!,
                        isAnalyzing = uiState.isAnalyzingImage,
                        onRemove = { viewModel.onImageSelected(null) }
                    )
                }
                SearchInputBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    isSearching = uiState.isSearching,
                    hasSelectedImage = uiState.selectedImageUri != null,
                    isAnalyzingImage = uiState.isAnalyzingImage,
                    onSubmit = {
                        if (uiState.selectedImageUri != null) {
                            requireScanAuthOrProceed { viewModel.detectImage() }
                        } else {
                            viewModel.searchMovies()
                        }
                    },
                    onGalleryClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCameraClick = {
                        val permission = android.Manifest.permission.CAMERA
                        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                            openCamera()
                        } else {
                            permissionLauncher.launch(permission)
                        }
                    }
                )
                AppBottomNavigationBar(
                    selectedTab = BottomNavTab.SEARCH,
                    onTabSelected = { tab ->
                        when (tab) {
                            BottomNavTab.HOME -> onNavigateBack()
                            BottomNavTab.SEARCH -> {}
                            BottomNavTab.LIBRARY -> onNavigateToLibrary()
                            BottomNavTab.PROFILE -> onNavigateToProfile()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(
                        text = stringResource(R.string.search_top_bar_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Faza 6 dorada (korisnikov feedback): rezultat pretrage slikom se VIŠE NE gasi kad
            // korisnik počne da kuca tekstualnu pretragu - ranije je ceo tekstualni rezultat/prazno
            // stanje bio uslovljen sa `visionResult == null`, što je sakrivalo tekstualne rezultate
            // čim bi vision kartica postojala, iako ViewModel stanje (`SearchUiState`) uopšte ne
            // briše `visionResult` kad se kuca - ovo je bio čisto UI bag u uslovu ispod, ne u
            // ViewModel-u. Sad obe sekcije mogu da postoje istovremeno, naslagane.
            uiState.visionResult?.let { response ->
                item {
                    VisionResultSection(
                        response = response,
                        onClear = { viewModel.clearResults() },
                        onNavigateToDetail = onNavigateToDetail
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            if (uiState.error != null) {
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.error_generic_title),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(uiState.error!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (uiState.searchResults.isNotEmpty()) {
                if (uiState.visionResult != null) {
                    // Mala odvojena naslovna linija samo kad su OBA rezultata istovremeno na
                    // ekranu, da bude jasno da je ovo sad druga, nezavisna lista.
                    item {
                        SectionHeader(title = stringResource(R.string.search_text_results_header))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                items(uiState.searchResults, key = { it.tmdbId }) { movie ->
                    MovieListRow(
                        title = movie.title,
                        posterPath = movie.posterPath,
                        metaText = movie.releaseDate?.take(4),
                        description = movie.overview,
                        ratingText = "${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}/10",
                        onClick = { onNavigateToDetail(movie.tmdbId) }
                    )
                }
            } else if (uiState.visionResult == null && uiState.error == null && uiState.searchQuery.isBlank()) {
                item {
                    SearchEmptyState()
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Prazno stanje pre prve pretrage - dokument (2.3) pominje da OVDE MOŽE da stoji "Nedavne
 * pretrage"/trending predlozi, ali to zahteva novi backend/state (SearchUiState nema takvo polje) -
 * van obima čisto vizuelnog redizajna. Umesto praznog ekrana, prijateljska poruka.
 */
@Composable
private fun SearchEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.search_empty_state_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.search_empty_state_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Kompaktna traka iznad input bara kad je slika izabrana ali još nije analizirana - isti obrazac
 * kao "prikačena slika" pregled u chat aplikacijama (WhatsApp/Telegram stil), zamenjuje raniju
 * veliku (260dp) karticu za pregled slike sa dva odvojena dugmeta.
 */
@Composable
private fun SelectedImagePreviewRow(imageUri: String, isAnalyzing: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(imageUri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
            )
        }
        Text(
            text = if (isAnalyzing) stringResource(R.string.search_image_analyzing) else stringResource(R.string.search_image_preview_ready),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (!isAnalyzing) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_image_remove_content_description))
            }
        }
    }
}

/**
 * Docked input bar (Faza 6, MOVIELENS_DESIGN_PLAN.md 2.3): ikonica kamere/slike (levo, sa
 * `PremiumBadge` u gornjem desnom uglu - signalizira da pretraga slikom troši Gemini pozive, za
 * sada čisto vizuelno), tekstualno polje (centar, pilula), okruglo dugme za slanje (desno).
 * Dugme prioritetno šalje sliku na analizu ako je slika prikačena, inače pokreće tekstualnu
 * pretragu - ista logika kao ranija dva odvojena dugmeta, sad konsolidovana u jedno.
 */
@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    hasSelectedImage: Boolean,
    isAnalyzingImage: Boolean,
    onSubmit: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    var showImageSourceMenu by remember { mutableStateOf(false) }
    val submitEnabled = if (hasSelectedImage) !isAnalyzingImage else query.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            IconButton(
                onClick = { showImageSourceMenu = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.search_image_attach_content_description),
                    modifier = Modifier.size(18.dp)
                )
            }
            PremiumBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
            )
            DropdownMenu(
                expanded = showImageSourceMenu,
                onDismissRequest = { showImageSourceMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_image_source_gallery)) },
                    onClick = { showImageSourceMenu = false; onGalleryClick() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_image_source_camera)) },
                    onClick = { showImageSourceMenu = false; onCameraClick() }
                )
            }
        }

        TextField(
            value = query,
            onValueChange = onQueryChange,
            // Bag korisnik prijavio: fiksna .height(48.dp) je bila premala za M3 TextField-ov
            // podrazumevani unutrašnji padding, pa je donji deo teksta (repovi slova g/j/p/y) bio
            // odsečen. heightIn(min=) umesto height() - polje raste do svoje prirodne, neisečene
            // visine ako zatreba, umesto da se nasilno stisne.
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            placeholder = {
                Text(stringResource(R.string.search_input_placeholder), style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.search_clear_content_description))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        IconButton(
            onClick = onSubmit,
            enabled = submitEnabled,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (submitEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            if (isAnalyzingImage) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.search_submit_content_description),
                    tint = if (submitEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Jedno "sekundarno" TMDB podudaranje + procenat podudaranja iz njegove roditeljske rezolucije
 * (confidence je polje kandidata/rezolucije, ne pojedinačnog TmdbMatch-a - videti VisionModels.kt). */
private data class SecondaryMatchEntry(val match: TmdbMatch, val confidencePercent: Int?)

private fun buildSecondaryMatches(response: VisionSearchResponse, heroMatchId: Int?): List<SecondaryMatchEntry> {
    val result = mutableListOf<SecondaryMatchEntry>()
    response.resolutions.forEach { resolution ->
        val percent = resolution.candidate.confidence?.let { (it * 100).roundToInt() }
        resolution.matches.forEach { match ->
            if (match.id != heroMatchId) {
                result.add(SecondaryMatchEntry(match, percent))
            }
        }
    }
    return result
}

/**
 * Faza 6 redizajna (MOVIELENS_DESIGN_PLAN.md 2.3) - korisnikov feedback: rezultati pretrage
 * slikom su ranije ispisivali SVE detektovane filmove odjednom, ravno, nepregledno. Novo,
 * dvoslojno sažimanje: najbolje poklapanje uvek razvijeno (`HeroMatchCard`), ostala poklapanja
 * sažeta iza "Prikaži još N" (`ExpandableMatchCard`), svaki od tih redova je i sam proširiv.
 */
@Composable
private fun VisionResultSection(
    response: VisionSearchResponse,
    onClear: () -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val firstResolution = response.resolutions.firstOrNull()
    val heroMatch = firstResolution?.matches?.firstOrNull()
    val heroConfidencePercent = firstResolution?.candidate?.confidence?.let { (it * 100).roundToInt() }
    val secondaryMatches = remember(response) { buildSecondaryMatches(response, heroMatch?.id) }
    var secondaryExpanded by remember(response) { mutableStateOf(false) }
    var expandedMatchIds by remember(response) { mutableStateOf(setOf<Int>()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.search_vision_result_title),
            actionText = stringResource(R.string.search_vision_clear),
            onActionClick = onClear
        )
        Text(
            text = if (response.notMovieLike) {
                stringResource(R.string.search_vision_not_movie_like)
            } else {
                stringResource(R.string.search_vision_success_message)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        if (heroMatch == null) {
            Text(stringResource(R.string.search_vision_no_hero), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                text = stringResource(R.string.search_vision_best_match_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            HeroMatchCard(
                match = heroMatch,
                confidencePercent = heroConfidencePercent,
                onClick = { onNavigateToDetail(heroMatch.id) }
            )

            if (secondaryMatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                ExpandableMatchCard(
                    expanded = secondaryExpanded,
                    onToggleExpanded = { secondaryExpanded = !secondaryExpanded },
                    header = {
                        Text(
                            text = if (secondaryExpanded) {
                                stringResource(R.string.search_vision_hide_matches)
                            } else {
                                stringResource(R.string.search_vision_show_more_matches, secondaryMatches.size)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    expandedContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            secondaryMatches.forEach { entry ->
                                val isRowExpanded = entry.match.id in expandedMatchIds
                                SecondaryMatchRow(
                                    entry = entry,
                                    expanded = isRowExpanded,
                                    onToggle = {
                                        expandedMatchIds = if (isRowExpanded) {
                                            expandedMatchIds - entry.match.id
                                        } else {
                                            expandedMatchIds + entry.match.id
                                        }
                                    },
                                    onClick = { onNavigateToDetail(entry.match.id) }
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Hero kartica najboljeg poklapanja - veći poster, naslov, procenat podudaranja, i (ako igra u
 * bioskopu) prva 1-2 mesta prikazivanja direktno vidljiva. Napomena: `ScrapedMovie` (isto kao
 * `Showtime` na Movie Detail ekranu) nema polje za VREME termina - prikazuje se naziv bioskopa kao
 * chip umesto izmišljenog vremena.
 */
@Composable
private fun HeroMatchCard(match: TmdbMatch, confidencePercent: Int?, onClick: () -> Unit) {
    val poster = tmdbPosterUrl(match.posterPath, "w342")
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(130.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (poster != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(true).build(),
                        contentDescription = match.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = match.title, style = MaterialTheme.typography.titleMedium)
                if (confidencePercent != null) {
                    Text(
                        text = stringResource(R.string.search_vision_match_percent, confidencePercent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                match.releaseDate?.take(4)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (match.cinemaShowtimes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        match.cinemaShowtimes.take(2).forEach { show ->
                            GenreChip(text = show.cinemaName)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Red sekundarnog poklapanja unutar sažete liste - mali poster thumb + naslov + %. Sam je proširiv
 * (ako ima bioskopskih termina) klikom otkriva `ShowtimeCard` listu za taj konkretan film, umesto
 * da se sve sipa odjednom na ekran.
 */
@Composable
private fun SecondaryMatchRow(
    entry: SecondaryMatchEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val match = entry.match
    val poster = tmdbPosterUrl(match.posterPath, "w154")
    val hasShowtimes = match.cinemaShowtimes.isNotEmpty()

    ExpandableMatchCard(
        expanded = expanded,
        onToggleExpanded = onToggle,
        headerTogglesExpansion = false,
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(58.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (poster != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(true).build(),
                            contentDescription = match.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = match.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.confidencePercent != null) {
                        Text(
                            text = stringResource(R.string.search_vision_match_percent, entry.confidencePercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        expandedContent = if (hasShowtimes) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    match.cinemaShowtimes.forEach { show ->
                        ShowtimeCard(show = show)
                    }
                }
            }
        } else null
    )
}

/** Kartica bioskopa unutar rezultata pretrage slikom - isti obrazac kao `CinemaShowtimeCard` na
 * Movie Detail ekranu (Faza 5), ovde za `ScrapedMovie` umesto `Showtime` (dva odvojena modela sa
 * istim poljima - videti VisionModels.kt/MovieModels.kt). */
@Composable
private fun ShowtimeCard(show: ScrapedMovie) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = show.cinemaName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = show.cinemaLocation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, show.bookingUrl.toUri()))
                    }
                }) {
                    Text(text = stringResource(R.string.movie_detail_open_link), style = MaterialTheme.typography.labelLarge)
                }
                if (show.isUpcoming) {
                    GenreChip(text = stringResource(R.string.movie_detail_upcoming_badge))
                }
            }
        }
    }
}

// Zajednička AppBottomNavigationBar komponenta (ui/components) — bez lokalnih duplikata.
// MovieSearchResultCard uklonjen - zamenjen deljenim MovieListRow (Faza 2/6, poslednji od 6
// dupliranih kartica navedenih u MOVIELENS_DESIGN_PLAN.md 0).
