package dev.milinko.movielens.ui.search

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.util.DevSettings
import dev.milinko.movielens.data.remote.vision.ScrapedMovie
import dev.milinko.movielens.data.remote.vision.TmdbMatch
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.auth.AuthViewModel
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.EmptyStateCard
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.PremiumBadge
import dev.milinko.movielens.ui.components.SectionHeader
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Search: text search over the whole TMDB catalog, image search (Gemini vision, signed-in users
 * only) and the "In cinemas" mode with semantic search over the current programme. The input bar is
 * docked at the bottom, reachable with a thumb.
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToDetail: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFriends: () -> Unit = {},
    onRequireSignIn: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
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
            // A fixed, readable message instead of the raw exception text; details stay in Logcat.
            Log.e("SearchScreen", "Error opening camera", e)
            Toast.makeText(context, context.getString(R.string.search_camera_error), Toast.LENGTH_LONG).show()
        }
    }

    val activity = LocalActivity.current
    var showCameraSettingsDialog by remember { mutableStateOf(false) }
    var showScanSignInDialog by remember { mutableStateOf(false) }
    var showCinemaModeImageDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                openCamera()
            } else {
                // When the system will no longer ask (rationale = false after a denial), offer the
                // app settings; otherwise a short explanation plus the alternative (gallery).
                val permanentlyDenied = activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, android.Manifest.permission.CAMERA)
                if (permanentlyDenied) {
                    showCameraSettingsDialog = true
                } else {
                    Toast.makeText(context, context.getString(R.string.search_camera_permission_denied), Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    // "Try again" on the error card - repeats whatever the user ran last.
    val retryLastSearch: () -> Unit = {
        if (uiState.selectedImageUri != null) {
            requireScanAuthOrProceed { viewModel.detectImage() }
        } else {
            viewModel.searchMovies()
        }
    }

    if (showScanSignInDialog) {
        AlertDialog(
            onDismissRequest = { showScanSignInDialog = false },
            title = { Text(stringResource(R.string.search_scan_signin_dialog_title)) },
            text = { Text(stringResource(R.string.search_scan_signin_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showScanSignInDialog = false
                    onRequireSignIn()
                }) { Text(stringResource(R.string.profile_sign_in)) }
            },
            dismissButton = {
                TextButton(onClick = { showScanSignInDialog = false }) {
                    Text(stringResource(R.string.profile_remove_cancel))
                }
            }
        )
    }

    if (showCinemaModeImageDialog) {
        AlertDialog(
            onDismissRequest = { showCinemaModeImageDialog = false },
            title = { Text(stringResource(R.string.search_image_cinema_mode_dialog_title)) },
            text = { Text(stringResource(R.string.search_image_cinema_mode_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCinemaModeImageDialog = false
                    viewModel.setCinemaMode(false)
                }) { Text(stringResource(R.string.search_image_cinema_mode_switch)) }
            },
            dismissButton = {
                TextButton(onClick = { showCinemaModeImageDialog = false }) {
                    Text(stringResource(R.string.profile_remove_cancel))
                }
            }
        )
    }

    if (showCameraSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showCameraSettingsDialog = false },
            title = { Text(stringResource(R.string.search_camera_settings_dialog_title)) },
            text = { Text(stringResource(R.string.search_camera_settings_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCameraSettingsDialog = false
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text(stringResource(R.string.search_camera_settings_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showCameraSettingsDialog = false }) {
                    Text(stringResource(R.string.profile_remove_cancel))
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            // The search field is at the bottom (easier to reach with a thumb). imePadding() is on
            // the WHOLE bottom cluster (image preview + input bar + app navigation), because
            // Scaffold has a single bottomBar slot, so the cluster rises above the keyboard
            // together.
            Column(modifier = Modifier.imePadding()) {
                uiState.selectedImageUri?.let { imageUri ->
                    SelectedImagePreviewRow(
                        imageUri = imageUri,
                        isAnalyzing = uiState.isAnalyzingImage,
                        onRemove = { viewModel.onImageSelected(null) }
                    )
                }
                SearchInputBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    // The spinner in the field also shows during the semantic search (separate
                    // flags in SearchUiState, see SearchViewModel).
                    isSearching = if (uiState.isCinemaMode) uiState.isSearchingCinemas else uiState.isSearching,
                    hasSelectedImage = uiState.selectedImageUri != null,
                    isAnalyzingImage = uiState.isAnalyzingImage,
                    isCinemaMode = uiState.isCinemaMode,
                    onSubmit = {
                        if (uiState.selectedImageUri != null) {
                            requireScanAuthOrProceed { viewModel.detectImage() }
                        } else {
                            viewModel.searchMovies()
                        }
                    },
                    canOpenImageMenu = {
                        when {
                            uiState.isCinemaMode -> {
                                showCinemaModeImageDialog = true
                                false
                            }
                            // Sign-in is required BEFORE picking an image, not discovered only when
                            // sending.
                            !authViewModel.isAlreadySignedIn() -> {
                                showScanSignInDialog = true
                                false
                            }
                            else -> true
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
                            BottomNavTab.FRIENDS -> onNavigateToFriends()
                            BottomNavTab.PROFILE -> onNavigateToProfile()
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        // Two pager pages (text search / in cinemas). The pager and the real ViewModel state
        // (uiState.isCinemaMode) are kept in sync in both directions, because the mode is not just
        // visual (it changes the debounce, disables image search...). No loop: setCinemaMode
        // returns early when the value is unchanged and animateScrollToPage is a no-op on the
        // target page.
        val pagerState = rememberPagerState(pageCount = { 2 })
        val pagerScope = rememberCoroutineScope()

        LaunchedEffect(pagerState.currentPage) {
            viewModel.setCinemaMode(pagerState.currentPage == 1)
        }
        LaunchedEffect(uiState.isCinemaMode) {
            val targetPage = if (uiState.isCinemaMode) 1 else 0
            if (pagerState.currentPage != targetPage) {
                pagerState.animateScrollToPage(targetPage)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
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
                Spacer(modifier = Modifier.height(12.dp))
                // Switches between the text search (whole TMDB catalog) and the semantic search
                // limited to the movies currently in cinemas.
                SearchModeToggle(
                    isCinemaMode = uiState.isCinemaMode,
                    onModeChange = { target ->
                        viewModel.setCinemaMode(target)
                        pagerScope.launch { pagerState.animateScrollToPage(if (target) 1 else 0) }
                    }
                )
                // Embedding provider switch, only in the "In cinemas" mode and only when
                // DevSettings.SHOW_LOCAL_EMBEDDING_ENGINE_OPTION is on (Render keeps the local
                // MiniLM index disabled, so "Local" would always return nothing there). Hidden
                // means the search always uses Gemini (uiState.useLocalEmbedding defaults to
                // false).
                if (uiState.isCinemaMode && DevSettings.SHOW_LOCAL_EMBEDDING_ENGINE_OPTION) {
                    Spacer(modifier = Modifier.height(8.dp))
                    EmbeddingEngineToggle(
                        useLocalEmbedding = uiState.useLocalEmbedding,
                        onChange = { viewModel.setUseLocalEmbedding(it) }
                    )
                }
                // An always visible explanation of how the "In cinemas" mode works (two search
                // layers, see SearchViewModel), not only in the empty state before the first
                // search.
                if (uiState.isCinemaMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.search_cinemas_live_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.error != null) {
                    // "Soft" errors (e.g. Gemini temporarily busy, see
                    // TransientServiceException.kt) get a calmer card (no red, no "Error", an info
                    // icon) - clearly different from real errors below.
                    if (uiState.isSoftError) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.error_transient_title),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        uiState.error ?: stringResource(R.string.error_transient_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = retryLastSearch) {
                                        Text(stringResource(R.string.state_retry))
                                    }
                                }
                            }
                        }
                    } else {
                        ErrorCard(
                            message = uiState.error ?: stringResource(R.string.error_generic_title),
                            onRetry = retryLastSearch
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                // The mode changes ONLY via SearchModeToggle; swiping used to switch it by accident
                // (e.g. while scrolling results), clearing the results and the picked image.
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                if (page == 0) {
                    TextSearchResultsPage(
                        uiState = uiState,
                        recentSearches = recentSearches,
                        onRecentQueryClick = { viewModel.selectRecentQuery(it) },
                        onRecentQueryRemove = { viewModel.removeRecentQuery(it) },
                        onClearAllRecent = { viewModel.clearRecentQueries() },
                        onNavigateToDetail = onNavigateToDetail,
                        onClearVision = { viewModel.clearResults() },
                        onRecordHistory = { viewModel.recordCurrentQueryInHistory() }
                    )
                } else {
                    CinemaResultsPage(
                        uiState = uiState,
                        onNavigateToDetail = onNavigateToDetail,
                        onRetryCatalog = viewModel::retryCinemaCatalog
                    )
                }
            }
        }
    }
}
