package dev.milinko.movielens.ui.profile

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.util.AppLanguageOption
import dev.milinko.movielens.util.AppLocale
import dev.milinko.movielens.util.DevSettings
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.GenreAffinity
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.ui.auth.AuthUiState
import dev.milinko.movielens.ui.auth.AuthViewModel
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.AppButton
import dev.milinko.movielens.ui.components.AppButtonVariant
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.RemoteAvatarImage
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.ui.components.SettingsDialog
import dev.milinko.movielens.ui.components.SignInRequiredState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Same limit as BIO_MAX_LENGTH on the backend (db/UsersTable.kt).
private const val BIO_MAX_LENGTH = 160

/**
 * Avatar circle with a thin `accentPrimary` border, shared by one's own profile headers and
 * FriendProfileScreen.
 */
@Composable
internal fun BorderedAvatar(avatarUrl: String?, fallbackLabel: String?, size: Int = 80) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            RemoteAvatarImage(
                url = avatarUrl,
                contentDescription = fallbackLabel,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        } else {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = fallbackLabel,
                    modifier = Modifier.fillMaxSize().padding(size.dp / 5),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** Read-only profile header: avatar, name, status line and statistics. */
@Composable
internal fun ProfileHeader(profile: dev.milinko.movielens.data.remote.user.UserProfile) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BorderedAvatar(avatarUrl = profile.avatar, fallbackLabel = profile.username)

        Spacer(modifier = Modifier.height(12.dp))
        Text(profile.username, style = MaterialTheme.typography.titleLarge)
        Text(
            profile.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!profile.bio.isNullOrBlank()) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 24.dp, end = 24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("${profile.stats.moviesWatched}", stringResource(R.string.profile_stat_watched))
            StatItem("${profile.stats.moviesRated}", stringResource(R.string.profile_stat_rated))
            StatItem("${profile.stats.friendCount}", stringResource(R.string.profile_stat_friends))
        }

        if (profile.favoriteGenres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.profile_favorite_genres, profile.favoriteGenres.joinToString(", ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

/**
 * Minimal header from the cached session (name, avatar) while the full profile is not loaded yet.
 */
@Composable
internal fun SessionIdentityHeader(user: SessionUserInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BorderedAvatar(avatarUrl = user.avatarUrl, fallbackLabel = user.username)

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = user.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username),
            style = MaterialTheme.typography.titleLarge
        )
        user.email?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

/** One statistic: a large value with a label below. */
@Composable
internal fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Profile header and profile editing in ONE view: the pencil/edit button switches between the
 * read-only header and the edit form, instead of showing the same data twice.
 *
 * A default `username` (when the user never set one) goes through `sanitizeUsername` before it
 * enters the text field, so saving any change (even just the picture or bio) does not fail the
 * backend's username check because of a raw Google name. Sanitizing is idempotent, so names the
 * user set themselves are untouched.
 *
 * `username`/`bio`/`pickedAvatarDataUri` are local state keyed by `isEditing`
 * (`remember(fullProfile?.userId, isEditing)`): every entry into edit mode starts from FRESH values
 * from `uiState`, which automatically discards unsaved changes on "Cancel".
 */
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
internal fun EditableProfileHeader(
    uiState: ProfileUiState,
    sessionUser: SessionUserInfo?,
    onSave: (username: String, bio: String?, newAvatarDataUri: String?) -> Unit,
    onDiscardFailedDraft: () -> Unit
) {
    val fullProfile = uiState.userProfile
    val rawDefaultUsername = fullProfile?.username ?: sessionUser?.username ?: ""
    val initialUsername = remember(rawDefaultUsername) {
        dev.milinko.movielens.util.sanitizeUsername(rawDefaultUsername)
    }
    val initialBio = fullProfile?.bio ?: ""
    val initialAvatar = fullProfile?.avatar ?: sessionUser?.avatarUrl
    val displayEmail = fullProfile?.email ?: sessionUser?.email

    var isEditing by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    // Input the server rejected (optimistic save, see ProfileViewModel.updateProfile) - the form
    // reopens with it instead of the old values.
    val failedDraft = uiState.failedProfileDraft
    var username by remember(fullProfile?.userId, isEditing, failedDraft) {
        mutableStateOf(failedDraft?.username ?: initialUsername)
    }
    var bio by remember(fullProfile?.userId, isEditing, failedDraft) {
        mutableStateOf(failedDraft?.let { it.bio.orEmpty() } ?: initialBio)
    }

    // The picture picked THIS time (a data URI, already compressed) - null until the user picks a
    // new one, in which case the current one (initialAvatar) is kept (see ViewModel.updateProfile).
    var pickedAvatarDataUri by remember(fullProfile?.userId, isEditing, failedDraft) {
        mutableStateOf(failedDraft?.avatarDataUri)
    }
    var isCompressingAvatar by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                isCompressingAvatar = true
                coroutineScope.launch {
                    val dataUri = withContext(Dispatchers.IO) {
                        dev.milinko.movielens.util.AvatarImage.compressToDataUri(context, uri)
                    }
                    isCompressingAvatar = false
                    if (dataUri != null) {
                        pickedAvatarDataUri = dataUri
                    } else {
                        Toast.makeText(context, context.getString(R.string.profile_avatar_pick_error), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    // Optimistic save: the form closes immediately on "Save" (see the button below); if the server
    // later rejects the change, it reopens here with the user's input and an error message.
    LaunchedEffect(failedDraft) {
        if (failedDraft != null) isEditing = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isEditing) {
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable(enabled = !isCompressingAvatar) {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                ) {
                    val previewAvatar = pickedAvatarDataUri ?: initialAvatar
                    if (isCompressingAvatar) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    } else if (!previewAvatar.isNullOrBlank()) {
                        RemoteAvatarImage(
                            url = previewAvatar,
                            contentDescription = stringResource(R.string.profile_edit_section_title),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primary) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().padding(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.profile_change_avatar),
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // The username is validated while typing, with the specific reason under the field.
            val usernameProblem = dev.milinko.movielens.util.validateUsername(username.trim())
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_username_label)) },
                isError = usernameProblem != null,
                supportingText = {
                    Text(
                        stringResource(
                            when (usernameProblem) {
                                null -> R.string.profile_username_helper
                                dev.milinko.movielens.util.UsernameProblem.TOO_SHORT -> R.string.profile_username_too_short
                                dev.milinko.movielens.util.UsernameProblem.TOO_LONG -> R.string.profile_username_too_long
                                dev.milinko.movielens.util.UsernameProblem.INVALID_CHARACTERS -> R.string.profile_username_invalid_chars
                            }
                        )
                    )
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // take(BIO_MAX_LENGTH) instead of rejecting the whole input, so pasted longer text is
            // trimmed; the counter turns red at the maximum.
            val bioAtLimit = bio.length >= BIO_MAX_LENGTH
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(BIO_MAX_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.profile_bio_label)) },
                supportingText = {
                    Text(
                        text = if (bioAtLimit) {
                            stringResource(R.string.profile_bio_limit_reached, BIO_MAX_LENGTH)
                        } else {
                            stringResource(R.string.profile_bio_helper, bio.length)
                        },
                        color = if (bioAtLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                minLines = 2,
                maxLines = 4
            )

            if (uiState.editProfileError != null) {
                Text(
                    text = uiState.editProfileError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppButton(
                    text = stringResource(R.string.profile_cancel_edit),
                    onClick = {
                        // Unsaved changes (including a picked picture) are not discarded without
                        // asking.
                        val hasChanges = username != initialUsername || bio != initialBio || pickedAvatarDataUri != null
                        if (hasChanges) {
                            showDiscardConfirm = true
                        } else {
                            onDiscardFailedDraft()
                            isEditing = false
                        }
                    },
                    variant = AppButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    text = stringResource(R.string.profile_save_changes),
                    onClick = {
                        onSave(username.trim(), bio.trim().ifBlank { null }, pickedAvatarDataUri)
                        isEditing = false
                    },
                    enabled = !isCompressingAvatar && usernameProblem == null,
                    modifier = Modifier.weight(1f)
                )
            }

            if (showDiscardConfirm) {
                AlertDialog(
                    onDismissRequest = { showDiscardConfirm = false },
                    title = { Text(stringResource(R.string.profile_discard_dialog_title)) },
                    text = { Text(stringResource(R.string.profile_discard_dialog_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDiscardConfirm = false
                            onDiscardFailedDraft()
                            isEditing = false
                        }) {
                            Text(stringResource(R.string.profile_discard_confirm), color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardConfirm = false }) {
                            Text(stringResource(R.string.profile_discard_keep_editing))
                        }
                    }
                )
            }
        } else {
            BorderedAvatar(avatarUrl = initialAvatar, fallbackLabel = initialUsername)

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = fullProfile?.username?.takeIf { it.isNotBlank() }
                    ?: initialUsername.ifBlank { null }
                    ?: stringResource(R.string.profile_default_username),
                style = MaterialTheme.typography.titleLarge
            )
            // The change is already shown (optimistically); this only quietly says the sync with
            // the server is still running.
            if (uiState.isSavingProfile) {
                Text(
                    text = stringResource(R.string.profile_saving),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            displayEmail?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!initialBio.isNullOrBlank()) {
                Text(
                    text = initialBio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 24.dp, end = 24.dp)
                )
            }

            if (fullProfile != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("${fullProfile.stats.moviesWatched}", stringResource(R.string.profile_stat_watched))
                    StatItem("${fullProfile.stats.moviesRated}", stringResource(R.string.profile_stat_rated))
                    StatItem("${fullProfile.stats.friendCount}", stringResource(R.string.profile_stat_friends))
                }
                if (fullProfile.favoriteGenres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.profile_favorite_genres, fullProfile.favoriteGenres.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // A clear button instead of a tiny pencil next to the name (hard to notice and to hit).
            Spacer(modifier = Modifier.height(16.dp))
            AppButton(
                text = stringResource(R.string.profile_edit_section_title),
                onClick = { isEditing = true },
                variant = AppButtonVariant.Secondary,
                leadingIcon = Icons.Default.Edit
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

// Uncertainty penalty constant (see TasteProfileSection): larger = genres with few movies are
// penalized more.
private const val TASTE_CONFIDENCE_PENALTY_Z = 1.5

/**
 * "Your taste" - average rating per genre. The top 3 genres are a compact horizontal row; the rest
 * are behind "Show N more genres" (ExpandableMatchCard), in a vertical list (genre + rating + bar).
 */
@Composable
internal fun TasteProfileSection(genres: List<GenreAffinity>) {
    // Ranking with an uncertainty penalty: CONFIDENCE_PENALTY_Z / sqrt(movieCount) is subtracted
    // from the average (in the spirit of lower-confidence-bound ranking). A genre with one movie
    // gets a large penalty (z=1.5, n=1 -> -1.5) while one with 4-6 movies gets much less (n=4 ->
    // -0.75, n=6 -> -0.61), so e.g. Action (9.5, 4 movies) ranks above Fantasy (10.0, 1 movie).
    // Shrinking toward the global average would not work: it can never reorder two genres whose
    // raw averages are on opposite sides of the global average.
    val sorted = remember(genres) {
        genres.sortedByDescending { affinity ->
            affinity.averageRating - TASTE_CONFIDENCE_PENALTY_Z / kotlin.math.sqrt(affinity.movieCount.toDouble())
        }
    }
    val remaining = sorted.drop(3)
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.profile_taste_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sorted.take(3), key = { it.genreId }) { affinity ->
                CompactGenreAffinityCard(affinity)
            }
        }

        if (remaining.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ExpandableMatchCard(
                expanded = expanded,
                onToggleExpanded = { expanded = !expanded },
                header = {
                    Text(
                        text = stringResource(
                            if (expanded) R.string.profile_taste_hide_genres else R.string.profile_taste_show_more_genres,
                            remaining.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                expandedContent = {
                    Column {
                        remaining.forEach { affinity ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GenreChip(text = affinity.genre)
                                    Text(
                                        text = stringResource(
                                            R.string.profile_taste_rating_count,
                                            String.format(LocalLocale.current.platformLocale, "%.1f", affinity.averageRating),
                                            affinity.movieCount
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { (affinity.averageRating / 10.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    color = MaterialTheme.colorScheme.secondary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

/** Compact card for one genre in the top-3 row: genre name, average rating and a bar. */
@Composable
internal fun CompactGenreAffinityCard(affinity: GenreAffinity) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        Text(
            text = affinity.genre,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.profile_taste_rating_count,
                String.format(LocalLocale.current.platformLocale, "%.1f", affinity.averageRating),
                affinity.movieCount
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (affinity.averageRating / 10.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(MaterialTheme.shapes.small),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}
