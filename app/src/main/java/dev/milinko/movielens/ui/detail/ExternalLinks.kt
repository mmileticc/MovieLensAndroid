package dev.milinko.movielens.ui.detail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.CastMember
import dev.milinko.movielens.data.remote.movie.MovieComment
import dev.milinko.movielens.data.remote.movie.Showtime
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.data.remote.vision.tmdbPosterUrl
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.AppButtonVariant
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.FullScreenImageDialog
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.RatingStars
import dev.milinko.movielens.ui.components.RemoteAvatarImage
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.ui.components.YouTubeTrailerPlayer

/**
 * "Istraži više" linkovi: IMDb dugme (ako TMDB zna IMDb ID filma), inače TMDB dugme; uz to uvek i
 * manji "Više na TMDB-u" link. Otvaraju se u browser-u / IMDb aplikaciji, isto kao booking link.
 */
@Composable
internal fun ExternalLinksRow(tmdbId: Int, imdbId: String?) {
    val context = LocalContext.current
    val tmdbUrl = "https://www.themoviedb.org/movie/$tmdbId"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.movie_detail_explore_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (imdbId != null) {
            ExternalLinkChip(
                label = stringResource(R.string.movie_detail_open_imdb),
                badgeColor = Color(0xFFF5C518),
                textColor = Color(0xFF000000),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.imdb.com/title/$imdbId/".toUri())) }
            )
            Text(
                text = stringResource(R.string.movie_detail_more_on_tmdb),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { context.startActivity(Intent(Intent.ACTION_VIEW, tmdbUrl.toUri())) }
            )
        } else {
            ExternalLinkChip(
                label = stringResource(R.string.movie_detail_open_tmdb),
                badgeColor = Color(0xFF01B4E4),
                textColor = Color(0xFF0D253F),
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, tmdbUrl.toUri())) }
            )
        }
    }
}

@Composable
internal fun ExternalLinkChip(label: String, badgeColor: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(badgeColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = textColor)
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Mali bedž za dopunsku ocenu (IMDb/Rotten Tomatoes) sa OMDb-a, prikazan pored TMDB ocene na
 * Movie Detail ekranu (3. septembar 2026). Namerno prost tekstualni bedž umesto pravih logoa
 * (nema licenciranih ikonica u projektu) - boja pozadine dovoljno jasno signalizira izvor.
 */
@Composable
internal fun ExternalRatingBadge(label: String, value: String, badgeColor: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(badgeColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}
