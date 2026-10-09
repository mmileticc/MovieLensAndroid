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

/**
 * An incoming friend request: the sender's avatar and name; the whole card opens their profile
 * (a limited public preview) so they can be checked before deciding. "Reject" only asks the parent
 * to show a confirmation (see `requestPendingRejection` in FriendsScreen).
 */
@Composable
internal fun PendingRequestCard(
    request: PendingRequest,
    onAccept: () -> Unit,
    onRejectClick: () -> Unit,
    onOpenProfile: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpenProfile)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape)) {
                if (!request.avatarUrl.isNullOrBlank()) {
                    RemoteAvatarImage(
                        url = request.avatarUrl,
                        contentDescription = request.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = request.username,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }
            }
            Text(
                text = request.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.profile_accept_content_description), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRejectClick) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.profile_reject_content_description), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * A sent (outgoing) request: same layout as PendingRequestCard (avatar + card opening the public
 * profile), with a single "Cancel request" button.
 */
@Composable
internal fun SentRequestCard(
    request: PendingRequest,
    onCancelClick: () -> Unit,
    onOpenProfile: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onOpenProfile)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape)) {
                if (!request.avatarUrl.isNullOrBlank()) {
                    RemoteAvatarImage(
                        url = request.avatarUrl,
                        contentDescription = request.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = request.username,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }
            }
            Text(
                text = request.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            TextButton(onClick = onCancelClick) {
                Text(stringResource(R.string.profile_cancel_request))
            }
        }
    }
}
