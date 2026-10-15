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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import dev.milinko.movielens.data.remote.movie.ShowtimeSlot
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
import dev.milinko.movielens.util.cinemaToday
import dev.milinko.movielens.util.isHallNumber
import dev.milinko.movielens.util.nextDay
import dev.milinko.movielens.util.safeWebLink
import dev.milinko.movielens.util.shortDayLabel
import dev.milinko.movielens.util.time
import dev.milinko.movielens.util.timesOnDay

/**
 * Day chips above the cinemas ("Today", "Tomorrow", "Mon 19.10."); scrolls sideways when the week
 * does not fit.
 */
@Composable
internal fun ShowtimeDayChips(days: List<String>, selected: String?, onSelect: (String) -> Unit) {
    val today = remember { cinemaToday() }
    val tomorrow = remember(today) { nextDay(today) }
    val locale = LocalLocale.current.platformLocale
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it }) { day ->
            val label = when (day) {
                today -> stringResource(R.string.showtimes_today)
                tomorrow -> stringResource(R.string.showtimes_tomorrow)
                else -> shortDayLabel(day, locale)
            }
            FilterChip(selected = day == selected, onClick = { onSelect(day) }, label = { Text(label) })
        }
    }
}

/**
 * Cinema card in the cinemas section: location icon, cinema name, address/distance below, an
 * "Upcoming" badge and either the screenings of [day] (each opens the ticket page of exactly that
 * screening) or, when the cinema has no known times, the link to its page for the movie.
 */
@Composable
internal fun CinemaShowtimeCard(show: Showtime, day: String? = null) {
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = show.cinemaName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = show.cinemaLocation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (show.distanceKm != null) {
                    Text(
                        text = "${String.format(LocalLocale.current.platformLocale, "%.1f", show.distanceKm)} km",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (show.times.isNullOrEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = { openLink(context, show.bookingUrl) }) {
                        Text(text = stringResource(R.string.movie_detail_open_link), style = MaterialTheme.typography.labelLarge)
                    }
                    if (show.isUpcoming) {
                        GenreChip(text = stringResource(R.string.movie_detail_upcoming_badge))
                    }
                }
            } else {
                val slots = timesOnDay(show, day)
                if (slots.isEmpty()) {
                    Text(
                        text = stringResource(R.string.showtimes_none_on_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ShowtimeSlots(slots = slots, cinema = show)
                }
            }
        }
    }
}

/** The screenings of one day as tappable time boxes ("19:00", below "3D · Hall 2"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShowtimeSlots(slots: List<ShowtimeSlot>, cinema: Showtime) {
    val context = LocalContext.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.forEach { slot ->
            val hall = slot.hall?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (isHallNumber(it)) stringResource(R.string.showtimes_hall_number, it) else it
            }
            val details = listOfNotNull(slot.format, hall).joinToString(" · ")
            val description = stringResource(R.string.showtimes_buy_content_description, slot.time(), cinema.cinemaLocation)
            Surface(
                // The screening's own ticket page, else the cinema's page for the movie.
                onClick = { openLink(context, safeWebLink(slot.bookingUrl) ?: cinema.bookingUrl) },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.semantics { contentDescription = description }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = slot.time(), style = MaterialTheme.typography.titleSmall)
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Opens a web link in the browser; anything that is not an http(s) link is ignored. */
private fun openLink(context: android.content.Context, url: String) {
    val safe = safeWebLink(url) ?: return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, safe.toUri())) }
}

/**
 * "Friends watched this movie" badge, a simple query over the friends' WATCHED data. Tapping a
 * friend's name opens their profile.
 */
@Composable
internal fun FriendsWatchedBadge(friends: List<FriendWatched>, onFriendClick: (String) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (friends.size == 1) {
                    stringResource(R.string.movie_detail_friends_watched_one)
                } else {
                    stringResource(R.string.movie_detail_friends_watched_many, friends.size)
                },
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            friends.forEach { friend ->
                // Only the name and rating; friends' notes were replaced by public comments
                // (CommentsSection).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFriendClick(friend.friendId) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = friend.friendName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (friend.rating != null) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${friend.rating}/10",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Avatar card for the horizontal cast row: round photo with the name below. `AsyncImage` needs
 * `fillMaxSize()` BEFORE `clip()`, otherwise the image renders at a small default size in the
 * top-left corner of the Box.
 */
@Composable
internal fun CastAvatarCard(actor: CastMember, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (actor.profilePath != null) {
                AsyncImage(
                    model = tmdbPosterUrl(actor.profilePath, "w185"),
                    contentDescription = actor.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = actor.name,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        actor.character?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
