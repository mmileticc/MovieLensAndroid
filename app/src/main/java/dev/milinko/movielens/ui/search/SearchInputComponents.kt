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
import androidx.compose.runtime.collectAsState
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
 * Segmented toggle "All movies" / "In cinemas". The project has no segmented control component
 * (SecondaryTabRow is for real pages), so this is a minimal one in the same visual language as
 * `GenreChip`, with the selected segment highlighted (primary background / onPrimary text).
 */
@Composable
internal fun SearchModeToggle(isCinemaMode: Boolean, onModeChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        SearchModeSegment(
            text = stringResource(R.string.search_mode_all),
            selected = !isCinemaMode,
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f)
        )
        SearchModeSegment(
            text = stringResource(R.string.search_mode_cinemas),
            selected = isCinemaMode,
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Toggle between the Gemini and the local MiniLM embedding engine for the "In cinemas" search (to
 * compare their results; shown only when enabled in DevSettings). A deliberately smaller, secondary
 * variant of [SearchModeSegment]: wraps its content, smaller text and a `secondaryContainer`
 * background, so it reads as a setting inside the mode rather than a third search mode.
 */
@Composable
internal fun EmbeddingEngineToggle(useLocalEmbedding: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .wrapContentWidth(Alignment.Start)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(2.dp)
    ) {
        EmbeddingEngineSegment(
            text = stringResource(R.string.search_engine_gemini),
            selected = !useLocalEmbedding,
            onClick = { onChange(false) }
        )
        EmbeddingEngineSegment(
            text = stringResource(R.string.search_engine_local),
            selected = useLocalEmbedding,
            onClick = { onChange(true) }
        )
    }
}

/** One segment of [EmbeddingEngineToggle]. */
@Composable
internal fun EmbeddingEngineSegment(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One segment of [SearchModeToggle]. */
@Composable
internal fun SearchModeSegment(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A "recent search" chip: tapping runs the search again, X removes it from the history. Same
 * visual language as GenreChip, but clickable and with an X.
 */
@Composable
internal fun RecentSearchChip(
    query: String,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = query,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // 40dp touch target (a 12dp icon alone is hard to hit with a finger).
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.search_recent_remove_content_description),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Compact strip above the input bar when an image is picked but not analyzed yet, like the
 * "attached image" preview in chat apps.
 */
@Composable
internal fun SelectedImagePreviewRow(imageUri: String, isAnalyzing: Boolean, onRemove: () -> Unit) {
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
 * Docked input bar: the camera/image button (left, with a `PremiumBadge` - image search uses Gemini
 * requests), the text field (center, pill-shaped) and a round send button (right). Send analyzes
 * the attached image if there is one, otherwise runs the text search.
 */
@Composable
internal fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    hasSelectedImage: Boolean,
    isAnalyzingImage: Boolean,
    isCinemaMode: Boolean,
    onSubmit: () -> Unit,
    // Asks the parent whether the Gallery/Camera menu may open - if not (sign-in required / "In
    // cinemas" mode) the parent explains why instead of showing a grey, "broken" icon.
    canOpenImageMenu: () -> Boolean,
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
            // Image search (Gemini vision) exists only in the text mode. In the "In cinemas" mode
            // the button is dimmed, and tapping it explains why and offers to switch (see
            // canOpenImageMenu).
            IconButton(
                onClick = { if (canOpenImageMenu()) showImageSourceMenu = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = stringResource(R.string.search_image_attach_content_description),
                    modifier = Modifier.size(18.dp),
                    tint = if (isCinemaMode) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (!isCinemaMode) {
                PremiumBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                )
            }
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
            // heightIn(min =) instead of a fixed height: a fixed 48dp was too small for the M3
            // TextField's default padding and cut off descenders (g/j/p/y).
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
            placeholder = {
                val placeholderRes = if (isCinemaMode) R.string.search_input_placeholder_cinemas else R.string.search_input_placeholder
                Text(stringResource(placeholderRes), style = MaterialTheme.typography.bodyMedium)
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
