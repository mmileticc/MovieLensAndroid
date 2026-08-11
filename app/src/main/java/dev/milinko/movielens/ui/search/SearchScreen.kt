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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.data.remote.vision.CandidateResolution
import dev.milinko.movielens.data.remote.vision.ScrapedMovie
import dev.milinko.movielens.data.remote.vision.TmdbMatch
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.auth.AuthViewModel
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import java.io.File

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
                "Za skeniranje slike moraš prvo da se uloguješ.",
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
            Toast.makeText(context, "Greška pri otvaranju kamere: ${e.message}", Toast.LENGTH_LONG).show()
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Pretraži filmove",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                TextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    placeholder = { Text("Pretraži po naslovu...") },
                    singleLine = true,
                    trailingIcon = {
                        if (uiState.isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.searchMovies() }),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Text(
                    "ILI pretraži preko slike/kamere",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (uiState.selectedImageUri != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(uiState.selectedImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Selected",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                contentScale = ContentScale.Fit
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.28f))
                                    .padding(16.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (uiState.isAnalyzingImage) {
                                    Button(
                                        onClick = { },
                                        enabled = false
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Analiziram sliku...")
                                    }
                                } else {
                                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Button(onClick = { viewModel.onImageSelected(null) }) {
                                            Text("Ukloni sliku")
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                requireScanAuthOrProceed {
                                                    viewModel.detectImage()
                                                }
                                            }
                                        ) {
                                            Text("Analiziraj sliku")
                                        }
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Polje za skeniranje", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "Postavi sliku ili koristi kameru da pretražiš film",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Galerija")
                                    }
                                    Button(
                                        onClick = {
                                            val permission = android.Manifest.permission.CAMERA
                                            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                                                openCamera()
                                            } else {
                                                permissionLauncher.launch(permission)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Kamera")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            uiState.visionResult?.let { response ->
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    VisionResultSection(response = response, onClear = { viewModel.clearResults() })
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (uiState.error != null) {
                item {
                    ElevatedCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Greška", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(uiState.error!!, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            items(uiState.searchResults) { movie ->
                MovieSearchResultCard(
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
}

@Composable
private fun VisionResultSection(
    response: VisionSearchResponse,
    onClear: () -> Unit
) {
    val firstResolution = response.resolutions.firstOrNull()
    val heroMatch = firstResolution?.matches?.firstOrNull()
    val heroPoster = heroMatch?.posterPath
    val totalMatches = response.resolutions.sumOf { it.matches.size }
    val totalShowtimes = response.resolutions.sumOf { resolution ->
        resolution.matches.sumOf { it.cinemaShowtimes.size }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rezultat analize", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (response.notMovieLike) "Slika ne liči na film/poster" else "Backend je vratio kandidate i TMDB podudaranja",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClear) { Text("Obriši") }
            }

            Spacer(modifier = Modifier.height(12.dp))

            heroMatch?.let { match ->
                if (heroPoster != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(tmdbPosterUrl(heroPoster, "w780"))
                            .crossfade(true)
                            .build(),
                        contentDescription = match.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(match.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        match.releaseDate?.takeIf { it.isNotBlank() },
                        match.voteAverage?.let { String.format(LocalLocale.current.platformLocale, "%.1f/10", it) },
                        match.popularity?.let { "Popularity: ${String.format(LocalLocale.current.platformLocale, "%.1f", it)}" }
                    ).joinToString(" · "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                match.overview?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(it, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatPill(label = "Podudaranja", value = totalMatches.toString())
                    StatPill(label = "Termini", value = totalShowtimes.toString())
                }
            } ?: run {
                Text("Nema top match rezultata za prikaz.")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Rezolucije", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            response.resolutions.take(4).forEachIndexed { index, resolution ->
                ResolutionInlineCard(index + 1, resolution)
                if (index < response.resolutions.take(4).lastIndex) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    ElevatedCard {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ResolutionInlineCard(index: Int, resolution: CandidateResolution) {
    val candidate = resolution.candidate
    ElevatedCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$index. ${candidate.title}${candidate.year?.let { " ($it)" } ?: ""}",
                fontWeight = FontWeight.SemiBold
            )
            candidate.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (resolution.matches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                resolution.matches.take(3).forEach { match ->
                    MatchInlineCard(match = match)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Nema TMDB podudaranja za ovu rezoluciju.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MatchInlineCard(match: TmdbMatch) {
    val poster = tmdbPosterUrl(match.posterPath, "w342")
    ElevatedCard {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (poster != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(true).build(),
                        contentDescription = match.title,
                        modifier = Modifier
                            .width(70.dp)
                            .height(105.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(match.title, fontWeight = FontWeight.SemiBold)
                    match.releaseDate?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    match.voteAverage?.let {
                        Text("${String.format(LocalLocale.current.platformLocale, "%.1f", it)}/10", fontSize = 12.sp)
                    }
                    match.overview?.takeIf { it.isNotBlank() }?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4)
                    }
                }
            }

            if (match.cinemaShowtimes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Bioskopski termini", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    match.cinemaShowtimes.forEach { show ->
                        ShowtimeCard(show = show)
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowtimeCard(show: ScrapedMovie) {
    val context = LocalContext.current
    ElevatedCard {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(show.cinemaName, fontWeight = FontWeight.SemiBold)
            Text(show.cinemaLocation, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, show.bookingUrl.toUri())
                        context.startActivity(intent)
                    }
                }) {
                    Text("Otvori link")
                }
                if (show.isUpcoming) {
                    Text("Uskoro", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MovieSearchResultCard(
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
            if (movie.overview != null && movie.overview.isNotEmpty()) {
                Text(
                    movie.overview,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFF5C518))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${String.format(LocalLocale.current.platformLocale, "%.1f", movie.voteAverage)}/10", fontSize = 11.sp)
            }
        }
    }
}

// Zajednička AppBottomNavigationBar komponenta (ui/components) — bez lokalnih duplikata.

