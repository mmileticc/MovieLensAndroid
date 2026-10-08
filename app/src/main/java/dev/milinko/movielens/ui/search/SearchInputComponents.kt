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
 * Faza D (17. avgust 2026, predlog mentora Marka) - segmentovani toggle "Sve filmove" / "U
 * bioskopima". Nema gotove segmented-control komponente u projektu (samo `SecondaryTabRow` za
 * Profile/Library tabove, koji vodi računa o zasebnim stranicama - ovde nema stranica, samo
 * prekidač za jedan search bar), pa je napravljena minimalna verzija istim vizuelnim jezikom kao
 * `GenreChip` (surfaceVariant pozadina + outline ivica), samo sa dva klikabilna segmenta i
 * markiranim izabranim (primary pozadina/onPrimary tekst).
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
 * Faza D dorada (korisnički zahtev za poređenje Gemini vs. lokalni MiniLM embedding "da bih
 * pogledao razliku u radu", pa naknadno "da ne bude isto veliko kao ova gore podela... manji
 * togle") - NAMERNO sitnija/sekundarna varijanta [SearchModeSegment] jezika: `wrapContentWidth`
 * umesto `fillMaxWidth` (ne razvlači se preko celog ekrana kao glavni mod toggle), manji tekst
 * (`labelSmall`) i manji unutrašnji padding, `secondaryContainer` pozadina da vizuelno čita kao
 * "podešavanje" unutar "U bioskopima" moda, ne kao ravnopravan treći mod pretrage.
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

/** Jedan "nedavna pretraga" chip - klik ponovo pokreće tu pretragu, X uklanja iz istorije. Isti
 * vizuelni jezik kao GenreChip (surfaceVariant + outline border), samo klikabilan i sa X. */
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
        // 40dp meta (ranije 24dp sa ikonicom od 12dp - teško se pogađalo prstom).
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
 * Kompaktna traka iznad input bara kad je slika izabrana ali još nije analizirana - isti obrazac
 * kao "prikačena slika" pregled u chat aplikacijama (WhatsApp/Telegram stil), zamenjuje raniju
 * veliku (260dp) karticu za pregled slike sa dva odvojena dugmeta.
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
 * Docked input bar (Faza 6, MOVIELENS_DESIGN_PLAN.md 2.3): ikonica kamere/slike (levo, sa
 * `PremiumBadge` u gornjem desnom uglu - signalizira da pretraga slikom troši Gemini pozive, za
 * sada čisto vizuelno), tekstualno polje (centar, pilula), okruglo dugme za slanje (desno).
 * Dugme prioritetno šalje sliku na analizu ako je slika prikačena, inače pokreće tekstualnu
 * pretragu - ista logika kao ranija dva odvojena dugmeta, sad konsolidovana u jedno.
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
    // Pita roditelja da li sme da otvori meni Galerija/Kamera - roditelj sam prikazuje objašnjenje
    // ako ne sme (prijava potrebna / "U bioskopima" mod) umesto sive, "pokvarene" ikonice.
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
            // Faza D - pretraga slikom (Gemini vision) postoji samo u tekstualnom modu. UX dorada
            // (oktobar 2026): u "U bioskopima" modu dugme više nije onemogućeno bez objašnjenja -
            // samo je prigušeno, a klik objašnjava zašto i nudi prelazak (videti canOpenImageMenu).
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
            // Bag korisnik prijavio: fiksna .height(48.dp) je bila premala za M3 TextField-ov
            // podrazumevani unutrašnji padding, pa je donji deo teksta (repovi slova g/j/p/y) bio
            // odsečen. heightIn(min=) umesto height() - polje raste do svoje prirodne, neisečene
            // visine ako zatreba, umesto da se nasilno stisne.
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
