package dev.milinko.movielens.ui.chat.v2

import android.content.pm.PackageManager.PERMISSION_GRANTED
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.data.remote.vision.CandidateResolution
import dev.milinko.movielens.data.remote.vision.TmdbMatch
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.SettingsDialog
import java.io.File
import java.util.Locale

private enum class MainSection { Scan, Result, Discovery, Library, Settings }
private enum class ResultTab { Info, Cast, Trailers }

private data class AppMovie(
    val title: String,
    val year: Int,
    val genre: String,
    val emoji: String,
    val score: String,
    val posterGradient: List<Color>
)

@Composable
fun ChatScreenV2(
    viewModel: ChatViewModelV2 = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val context = LocalContext.current

    var mainSection by rememberSaveable { mutableStateOf(MainSection.Scan) }
    var resultTab by rememberSaveable { mutableStateOf(ResultTab.Info) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            viewModel.onImageSelected(uri?.toString())
            showSearchOverlay = false
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                viewModel.onImageSelected(cameraImageUri?.toString())
                showSearchOverlay = false
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
            Log.e("ChatScreenV2", "Error opening camera", e)
            Toast.makeText(context, "Greška pri otvaranju kamere: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) openCamera() else showPermissionRationale = true
        }
    )

    LaunchedEffect(uiState.result) {
        if (uiState.result != null) {
            mainSection = MainSection.Result
            resultTab = ResultTab.Info
        }
    }

    val scanAction: () -> Unit = {
        if (uiState.selectedImageUri == null) {
            showSearchOverlay = true
        } else {
            viewModel.detectSelectedImage()
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentUrl = baseUrl,
            onDismiss = { showSettingsDialog = false },
            onSave = {
                viewModel.updateBaseUrl(it)
                showSettingsDialog = false
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Reset rezultata") },
            text = { Text("Da li želiš da obrišeš trenutno prikazani odgovor?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearResult()
                    showClearDialog = false
                }) {
                    Text("Obriši", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Otkaži") }
            }
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Dozvola za kameru") },
            text = { Text("Kamera je potrebna za direktno slikanje i slanje slike na backend.") },
            confirmButton = {
                Button(onClick = {
                    showPermissionRationale = false
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }) { Text("Idi u podešavanja") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) { Text("Otkaži") }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            MovieScanFab(
                enabled = !uiState.isLoading,
                onClick = scanAction
            )
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
        bottomBar = {
            MovieBottomBar(
                activeSection = mainSection,
                onSelect = { section ->
                    mainSection = section
                    if (section == MainSection.Settings) showSettingsDialog = true
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF070710), Color(0xFF0F0F1A), Color(0xFF0B0B12))
                    )
                )
        ) {
            when (mainSection) {
                MainSection.Scan -> ScanSection(
                    baseUrl = baseUrl,
                    uiState = uiState,
                    onOpenSettings = { showSettingsDialog = true },
                    onOpenSearchOverlay = { showSearchOverlay = true },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCameraCapture = {
                        val permission = android.Manifest.permission.CAMERA
                        if (checkSelfPermission(context, permission) == PERMISSION_GRANTED) {
                            openCamera()
                        } else {
                            permissionLauncher.launch(permission)
                        }
                    },
                    onRemoveImage = { viewModel.onImageSelected(null) },
                    onScan = scanAction
                )

                MainSection.Result -> {
                    val response = uiState.result
                    if (response != null) {
                        VisionResultSection(
                            response = response,
                            tab = resultTab,
                            onTabChange = { resultTab = it }
                        )
                    } else {
                        ScanSection(
                            baseUrl = baseUrl,
                            uiState = uiState,
                            onOpenSettings = { showSettingsDialog = true },
                            onOpenSearchOverlay = { showSearchOverlay = true },
                            onPickImage = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onCameraCapture = {
                                val permission = android.Manifest.permission.CAMERA
                                if (checkSelfPermission(context, permission) == PERMISSION_GRANTED) {
                                    openCamera()
                                } else {
                                    permissionLauncher.launch(permission)
                                }
                            },
                            onRemoveImage = { viewModel.onImageSelected(null) },
                            onScan = scanAction
                        )
                    }
                }

                MainSection.Discovery -> DiscoverySection(
                    onOpenMovie = { mainSection = MainSection.Result },
                    featured = uiState.result
                )

                MainSection.Library -> LibrarySection(
                    featured = uiState.result
                )

                MainSection.Settings -> SettingsSection(
                    baseUrl = baseUrl,
                    onEditBackend = { showSettingsDialog = true }
                )
            }

            if (mainSection == MainSection.Scan && uiState.result != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 88.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ResultPreviewCard(
                        response = uiState.result!!,
                        onOpen = { mainSection = MainSection.Result }
                    )
                }
            }

            if (uiState.isLoading) {
                LoadingOverlay()
            }

            if (showSearchOverlay) {
                SearchOverlay(
                    onDismiss = { showSearchOverlay = false },
                    onPickGallery = {
                        showSearchOverlay = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenCamera = {
                        showSearchOverlay = false
                        val permission = android.Manifest.permission.CAMERA
                        if (checkSelfPermission(context, permission) == PERMISSION_GRANTED) {
                            openCamera()
                        } else {
                            permissionLauncher.launch(permission)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanSection(
    baseUrl: String,
    uiState: VisionUiState,
    onOpenSettings: () -> Unit,
    onOpenSearchOverlay: () -> Unit,
    onPickImage: () -> Unit,
    onCameraCapture: () -> Unit,
    onRemoveImage: () -> Unit,
    onScan: () -> Unit
) {
    // Simplified, focused UI for poster scanning and result preview
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header row: title + settings
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Poster Scanner", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Edit backend", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Backend row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(baseUrl, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onOpenSettings) { Text("Edit", color = Color(0xFFE50914)) }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Image preview area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0D0D20), Color(0xFF1A0A1A))))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
        ) {
            if (uiState.selectedImageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(uiState.selectedImageUri).crossfade(true).build(),
                    contentDescription = "Selected poster",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No image selected", color = Color.White.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Use camera or pick from gallery to scan a poster.", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }

            // Action buttons overlay
            Row(modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundActionButton(icon = Icons.Default.PhotoLibrary, onClick = onPickImage, background = Color.Black.copy(alpha = 0.45f))
                    if (uiState.selectedImageUri != null) {
                        RoundActionButton(icon = Icons.Default.Delete, onClick = onRemoveImage, background = Color.Black.copy(alpha = 0.55f), tint = Color.White)
                    } else {
                        RoundActionButton(icon = Icons.Default.CameraAlt, onClick = onCameraCapture, background = Color.Black.copy(alpha = 0.45f))
                    }
                }

                // Search button
                Button(
                    onClick = onScan,
                    enabled = uiState.selectedImageUri != null && !uiState.isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Search", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick compact results preview (top matches) - graphical and responsive
        uiState.result?.let { response ->
            SectionTitle(title = "Top matches")
            Spacer(modifier = Modifier.height(10.dp))
            val matches = response.resolutions.firstOrNull()?.matches ?: emptyList()
            if (matches.isEmpty()) {
                Text("No matches found yet.", color = Color.White.copy(alpha = 0.6f))
            } else {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    matches.take(6).forEach { m ->
                        ElevatedCard(
                            modifier = Modifier.width(160.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF11111A))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                if (m.posterPath != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(tmdbPosterUrl(m.posterPath)).crossfade(true).build(),
                                        contentDescription = m.title,
                                        modifier = Modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(modifier = Modifier.height(200.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.04f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(m.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(6.dp))
                                m.voteAverage?.let { v ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Ensure progress is a Float to match the CircularProgressIndicator overload
                                        CircularProgressIndicator(
                                            progress = (v.toFloat() / 10f).coerceIn(0f, 1f),
                                            color = Color(0xFFF5C518),
                                            strokeWidth = 4.dp,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(String.format(Locale.getDefault(), "%.1f/10", v), color = Color.White.copy(alpha = 0.9f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ResultPreviewCard(
    response: VisionSearchResponse,
    onOpen: () -> Unit
) {
    val hero = response.resolutions.firstOrNull()?.matches?.firstOrNull()
    val heroCandidate = response.resolutions.firstOrNull()?.candidate

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF11111C))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = heroCandidate?.title ?: "Vision result",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (response.notMovieLike) {
                            "Slika ne liči na poster/isječak"
                        } else {
                            "${response.resolutions.size} kandidat(a) · ${response.queryLanguage ?: "en"}"
                        },
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = onOpen) {
                    Text("Open", color = Color(0xFFE50914))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hero?.posterPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(tmdbPosterUrl(hero.posterPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = hero.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(22.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                ResultHeroPlaceholder(
                    title = heroCandidate?.title ?: "Unknown",
                    subtitle = heroCandidate?.notes ?: response.uncertaintyReason
                )
            }
        }
    }
}

@Composable
private fun DiscoverySection(
    onOpenMovie: () -> Unit,
    featured: VisionSearchResponse?
) {
    val scrollState = rememberScrollState()
    val trending = remember {
        listOf(
            AppMovie("Gladiator II", 2024, "Action / Epic", "⚔️", "7.7", listOf(Color(0xFF3A1A00), Color(0xFF5A2A00))),
            AppMovie("The Substance", 2024, "Body Horror", "💊", "7.5", listOf(Color(0xFF1A0A1A), Color(0xFF3A1A3A))),
            AppMovie("Conclave", 2024, "Thriller / Drama", "🕍", "7.4", listOf(Color(0xFF0A1A0A), Color(0xFF1A3A1A)))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Discover", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("See all", color = Color(0xFFE50914), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        FeatureCard(
            title = "Weekly Top 10",
            subtitle = "Curated from 50M+ reviews",
            buttonText = "Explore Now",
            onClick = onOpenMovie
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle(title = "Trending this week")
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            trending.forEach { movie ->
                TrendingRow(movie = movie, onClick = onOpenMovie)
            }
        }

        featured?.let {
            Spacer(modifier = Modifier.height(18.dp))
            SectionTitle(title = "Latest scan")
            Spacer(modifier = Modifier.height(12.dp))
            ResultPreviewCard(response = it, onOpen = onOpenMovie)
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun LibrarySection(
    featured: VisionSearchResponse?
) {
    val scrollState = rememberScrollState()
    val libraryMovies = remember {
        listOf(
            AppMovie("Interstellar", 2014, "Sci‑Fi", "🚀", "Saved", listOf(Color(0xFF0A0A2E), Color(0xFF1A1A4E))),
            AppMovie("Dune: Part Two", 2024, "Sci‑Fi", "🏜️", "Saved", listOf(Color(0xFF2A1A00), Color(0xFF4A3000))),
            AppMovie("Alien: Romulus", 2024, "Horror", "👾", "Seen", listOf(Color(0xFF0A1A0A), Color(0xFF0A2A0A))),
            AppMovie("The Substance", 2024, "Horror", "💊", "Seen", listOf(Color(0xFF1A0A1A), Color(0xFF3A1A3A)))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Library", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${libraryMovies.size} titles", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        PillToggleRow(
            left = "Watchlist",
            right = "History",
            selectedLeft = true,
            onLeftClick = { }
        )

        Spacer(modifier = Modifier.height(16.dp))

        val rows = libraryMovies.chunked(2)
        rows.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { movie ->
                    Box(modifier = Modifier.weight(1f)) {
                        SmallPosterCard(movie = movie)
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        featured?.let {
            Spacer(modifier = Modifier.height(12.dp))
            SectionTitle(title = "Last scan")
            Spacer(modifier = Modifier.height(12.dp))
            ResultPreviewCard(response = it, onOpen = { })
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun SettingsSection(
    baseUrl: String,
    onEditBackend: () -> Unit
) {
    val scrollState = rememberScrollState()
    var darkMode by rememberSaveable { mutableStateOf(true) }
    var notifications by rememberSaveable { mutableStateOf(true) }
    var aiRecommendations by rememberSaveable { mutableStateOf(true) }
    var dataSaver by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Settings", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(
            title = "Backend URL",
            subtitle = baseUrl,
            actionText = "Edit",
            onAction = onEditBackend
        )

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(label = "Preferences")
        Spacer(modifier = Modifier.height(10.dp))

        SettingsToggleRow(
            title = "Dark Mode",
            checked = darkMode,
            onToggle = { darkMode = it },
            icon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White.copy(alpha = 0.75f)) }
        )
        SettingsToggleRow(
            title = "Notifications",
            checked = notifications,
            onToggle = { notifications = it },
            icon = { Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White.copy(alpha = 0.75f)) }
        )
        SettingsToggleRow(
            title = "AI Recommendations",
            checked = aiRecommendations,
            onToggle = { aiRecommendations = it },
            icon = { Icon(Icons.Default.Explore, contentDescription = null, tint = Color.White.copy(alpha = 0.75f)) }
        )
        SettingsToggleRow(
            title = "Data Saving Mode",
            checked = dataSaver,
            onToggle = { dataSaver = it },
            icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White.copy(alpha = 0.75f)) }
        )

        Spacer(modifier = Modifier.height(16.dp))
        SectionHeader(label = "About")
        Spacer(modifier = Modifier.height(10.dp))

        SettingsActionCard(title = "App Version", value = "2.4.1")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsActionCard(title = "Privacy Policy", value = "Open")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsActionCard(title = "Send Feedback", value = "Compose")

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun MovieBottomBar(
    activeSection: MainSection,
    onSelect: (MainSection) -> Unit
) {
    Surface(color = Color(0xFF0B0B12), tonalElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "Scan",
                icon = Icons.Default.CameraAlt,
                selected = activeSection == MainSection.Scan,
                onClick = { onSelect(MainSection.Scan) }
            )
            BottomNavItem(
                label = "Discover",
                icon = Icons.Default.Explore,
                selected = activeSection == MainSection.Discovery,
                onClick = { onSelect(MainSection.Discovery) }
            )
            Spacer(modifier = Modifier.width(72.dp))
            BottomNavItem(
                label = "Library",
                icon = Icons.Default.LibraryBooks,
                selected = activeSection == MainSection.Library,
                onClick = { onSelect(MainSection.Library) }
            )
            BottomNavItem(
                label = "Settings",
                icon = Icons.Default.Settings,
                selected = activeSection == MainSection.Settings,
                onClick = { onSelect(MainSection.Settings) }
            )
        }
    }
}

@Composable
private fun MovieScanFab(
    enabled: Boolean,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        containerColor = Color(0xFFE50914),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp, pressedElevation = 14.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = "Scan")
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
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) Color(0xFFE50914) else Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun StatusHeader(
    title: String,
    accent: String,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = accent,
            color = Color(0xFFE50914),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Text(
            text = "Search any movie or show…",
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CameraViewfinderCard(
    selectedImageUri: String?,
    onPickImage: () -> Unit,
    onCameraCapture: () -> Unit,
    onRemoveImage: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D0D20), Color(0xFF1A0A1A), Color(0xFF2A0A14))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x33E50914), Color.Transparent),
                        radius = 600f
                    )
                )
        )

        if (selectedImageUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(selectedImageUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Selected image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.30f))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundActionButton(icon = Icons.Default.Tune, onClick = onPickImage, background = Color.Black.copy(alpha = 0.45f))
                RoundActionButton(icon = Icons.Default.PhotoLibrary, onClick = onPickImage, background = Color.Black.copy(alpha = 0.45f))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x22E50914),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color(0x55E50914))
                ) {
                    Box(
                        modifier = Modifier.size(74.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Point at a poster or screen", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedImageUri?.let { "Image selected" } ?: "Use gallery or camera",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundActionButton(icon = Icons.Default.PhotoLibrary, onClick = onPickImage, background = Color.Black.copy(alpha = 0.45f))
                if (selectedImageUri != null) {
                    RoundActionButton(icon = Icons.Default.Delete, onClick = onRemoveImage, background = Color.Black.copy(alpha = 0.55f), tint = Color.White)
                } else {
                    RoundActionButton(icon = Icons.Default.CameraAlt, onClick = onCameraCapture, background = Color.Black.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Composable
private fun ScanIntroCard(onScan: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF12121D))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Scan this frame", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Odaberi sliku iz galerije ili kamere, a zatim pokreni prepoznavanje i dobićeš strukturiran rezultat sa kandidatima i TMDB match-evima.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onScan,
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan now")
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF141420))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color(0xFFE50914))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Analiziram sliku…", color = Color.White)
            }
        }
    }
}

@Composable
private fun SearchOverlay(
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onOpenCamera: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(onClick = onDismiss)
    ) {
        ElevatedCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF11111A))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Search movies, shows, actors…", color = Color.White.copy(alpha = 0.4f))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFFE50914))
                    }
                }

                Text("RECENT SEARCHES", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("Dune Part Two", "Christopher Nolan films", "Best sci-fi 2025").forEach {
                        AssistChip(
                            onClick = onDismiss,
                            label = { Text(it) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Text("QUICK ACTIONS", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickActionChip(text = "Gallery", icon = Icons.Default.PhotoLibrary, onClick = onPickGallery)
                    QuickActionChip(text = "Camera", icon = Icons.Default.CameraAlt, onClick = onOpenCamera)
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.48f).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF2A1111))) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Greška", color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(message, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SmallPosterCard(movie: AppMovie) {
    Column(modifier = Modifier.width(108.dp)) {
        Box(
            modifier = Modifier
                .width(108.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(movie.posterGradient))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(movie.emoji, fontSize = 32.sp)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                    .padding(8.dp)
            ) {
                Column {
                    Text(movie.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(movie.year.toString(), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
    }
}

@Composable
private fun TrendingRow(
    movie: AppMovie,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.04f)
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(movie.posterGradient)),
                contentAlignment = Alignment.Center
            ) { Text(movie.emoji, fontSize = 22.sp) }
            Column(modifier = Modifier.weight(1f)) {
                Text(movie.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(3.dp))
                Text(movie.year.toString() + " · " + movie.genre, color = Color.White.copy(alpha = 0.48f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF5C518), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${movie.score} · ${if (movie.title == "Gladiator II") "89% match" else if (movie.title == "The Substance") "76% match" else "81% match"}", color = Color(0xFFF5C518), fontSize = 12.sp)
                }
            }
            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A0A2E), Color(0xFF2A1040))))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(120.dp)
                .background(Color(0x22E50914), CircleShape)
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("AI PICKS FOR YOU", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Button(onClick = onClick, shape = RoundedCornerShape(14.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))) {
                Text(buttonText, color = Color.White)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(Locale.getDefault()),
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp
    )
}

@Composable
private fun PillToggleRow(
    left: String,
    right: String,
    selectedLeft: Boolean,
    onLeftClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(18.dp))
            .padding(4.dp)
    ) {
        PillTab(text = left, selected = selectedLeft, modifier = Modifier.weight(1f), onClick = onLeftClick)
        PillTab(text = right, selected = !selectedLeft, modifier = Modifier.weight(1f), onClick = { })
    }
}

@Composable
private fun PillTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFE50914) else Color.Transparent
    ) {
        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) Color.White else Color.White.copy(alpha = 0.65f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.04f)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onAction) { Text(actionText, color = Color(0xFFE50914)) }
        }
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    value: String
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.04f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(value, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.04f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                icon()
                Text(title, color = Color.White, fontSize = 14.sp)
            }
            Surface(
                shape = CircleShape,
                color = if (checked) Color(0xFFE50914) else Color.White.copy(alpha = 0.12f),
                modifier = Modifier.clickable { onToggle(!checked) }
            ) {
                Box(modifier = Modifier.size(width = 40.dp, height = 24.dp))
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    background: Color,
    tint: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ResultHeroPlaceholder(
    title: String,
    subtitle: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0A0A2E), Color(0xFF1A1A4E)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎬", fontSize = 54.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            subtitle?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun VisionResultSection(
    response: VisionSearchResponse,
    tab: ResultTab,
    onTabChange: (ResultTab) -> Unit
) {
    val first = response.resolutions.firstOrNull()
    val heroMatch = first?.matches?.firstOrNull()
    val heroTitle = first?.candidate?.title ?: "Vision result"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(18.dp))
                Text("Back", color = Color(0xFFE50914), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Text("9:41", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF0A0A2E), Color(0xFF1A1A4E), Color(0xFF2A0A1A))
                    )
                )
        ) {
            if (heroMatch?.posterPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(tmdbPosterUrl(heroMatch.posterPath))
                        .crossfade(true)
                        .build(),
                    contentDescription = heroTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                AssistChip(
                    onClick = { },
                    label = { Text(if (response.notMovieLike) "NOT MOVIE LIKE" else (first?.candidate?.type?.uppercase(Locale.getDefault()) ?: "MOVIE")) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = Color(0x22E50914),
                        labelColor = Color(0xFFFF6B6B)
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(heroTitle, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = listOfNotNull(
                        first?.candidate?.year?.toString(),
                        heroMatch?.releaseDate,
                        heroMatch?.voteAverage?.let { String.format(Locale.getDefault(), "%.1f/10", it) }
                    ).joinToString(" · ").ifBlank { response.uncertaintyReason ?: "Unknown" },
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionPill(text = "Watchlist", icon = Icons.Default.FavoriteBorder)
            ActionPill(text = "Cinemas", icon = Icons.Default.Explore)
            ActionPill(text = "Share", icon = Icons.Default.Share)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ResultTabChip(text = "Info", selected = tab == ResultTab.Info, onClick = { onTabChange(ResultTab.Info) })
            ResultTabChip(text = "Cast", selected = tab == ResultTab.Cast, onClick = { onTabChange(ResultTab.Cast) })
            ResultTabChip(text = "Trailers", selected = tab == ResultTab.Trailers, onClick = { onTabChange(ResultTab.Trailers) })
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (tab) {
            ResultTab.Info -> {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF11111A))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AI MATCH SCORE", color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = first?.candidate?.confidence?.let { "${(it * 100).toInt()}%" } ?: "—",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = response.uncertaintyReason ?: if (response.notMovieLike) "Image does not look like a poster/still" else "Excellent match candidates were found.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        InfoRow(label = "Language", value = response.queryLanguage ?: "—")
                        InfoRow(label = "Resolutions", value = response.resolutions.size.toString())
                        InfoRow(label = "Status", value = if (response.notMovieLike) "Not movie-like" else "Ready")
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                SectionTitle(title = "TMDB matches")
                Spacer(modifier = Modifier.height(10.dp))
                response.resolutions.forEachIndexed { index, resolution ->
                    ResolutionCard(index + 1, resolution)
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            ResultTab.Cast -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CastCard(name = "Matthew McConaughey", role = "Lead / ${heroTitle}")
                    CastCard(name = "Anne Hathaway", role = "Co-star")
                    CastCard(name = "Jessica Chastain", role = "Featured")
                    CastCard(name = "Michael Caine", role = "Supporting")
                }
            }

            ResultTab.Trailers -> {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TrailerCard(title = "Official Trailer", duration = "2:24", views = "42M views")
                    TrailerCard(title = "Scene Clip", duration = "0:58", views = "12M views")
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun ResultTabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.32f).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFE50914) else Color.White.copy(alpha = 0.05f)
    ) {
        Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.31f),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
            Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResolutionCard(index: Int, resolution: CandidateResolution) {
    val candidate = resolution.candidate
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF11111A))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "$index. ${candidate.title}${candidate.year?.let { " ($it)" } ?: ""}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                candidate.type?.let { MetaChip(it.uppercase(Locale.getDefault())) }
                candidate.confidence?.let { MetaChip("${(it * 100).toInt()}%") }
            }
            candidate.notes?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            if (resolution.matches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("TMDB matches", color = Color.White.copy(alpha = 0.48f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    resolution.matches.take(4).forEach { match ->
                        TmdbMatchRow(match = match)
                    }
                }
            }
        }
    }
}

@Composable
private fun TmdbMatchRow(match: TmdbMatch) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        val poster = tmdbPosterUrl(match.posterPath)
        if (poster != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(poster).crossfade(true).build(),
                contentDescription = match.title,
                modifier = Modifier
                    .size(width = 72.dp, height = 108.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 108.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(match.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            match.originalTitle?.takeIf { it.isNotBlank() && it != match.title }?.let {
                Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            match.releaseDate?.let {
                Text("Release: $it", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            }
            val stats = buildList {
                match.popularity?.let { add("Popularity: ${String.format(Locale.getDefault(), "%.1f", it)}") }
                match.voteAverage?.let { add("Vote: ${String.format(Locale.getDefault(), "%.1f", it)}") }
            }
            if (stats.isNotEmpty()) {
                Text(stats.joinToString(" · "), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            match.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    it,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0x22E50914)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color(0xFFFF6B6B),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CastCard(name: String, role: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.04f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF1A3A5E), Color(0xFF6A2A8E)))),
                contentAlignment = Alignment.Center
            ) {
                Text(name.first().toString(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(role, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TrailerCard(title: String, duration: String, views: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.04f)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Brush.linearGradient(listOf(Color(0xFF0A0A2E), Color(0xFF1A1A4E)))),
                contentAlignment = Alignment.Center
            ) {
                Surface(shape = CircleShape, color = Color(0xFFE50914).copy(alpha = 0.90f)) {
                    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$duration · $views", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}





