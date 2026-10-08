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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milinko.movielens.R
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
import dev.milinko.movielens.util.AppLanguageOption
import dev.milinko.movielens.util.AppLocale
import dev.milinko.movielens.util.DevSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tab "Prijatelji" u donjoj navigaciji: dolazni i poslati zahtevi, pretraga korisnika, lista
 * prijatelja i njihova aktivnost. Ranije je ovo bio tab unutar Profila; deli [ProfileViewModel]
 * (isti podaci i akcije), a Profil je ostao za sopstveni profil i podešavanja.
 */
@Composable
fun FriendsScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onNavigateToFriendProfile: (String) -> Unit,
    onNavigateToDetail: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionUser by viewModel.sessionUser.collectAsStateWithLifecycle()
    // Potvrda pre nego što se prijatelj stvarno ukloni - nema pravog "undo" jer bi ponovno
    // dodavanje zahtevalo da druga strana opet prihvati zahtev (nije simetrično kao watched-undo).
    var friendPendingRemoval by remember { mutableStateOf<FriendInfo?>(null) }
    // Odbijanje i otkazivanje zahteva su isto nepovratni (druga strana bi morala ponovo da pošalje
    // ili dobije zahtev), pa traže istu potvrdu. Par (id, ime za poruku).
    var requestPendingRejection by remember { mutableStateOf<PendingRequest?>(null) }
    var sentRequestPendingCancel by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val context = LocalContext.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileViewModel.ProfileUiEvent.ShowMessage ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    LaunchedEffect(uiState.friendActionError) {
        uiState.friendActionError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeFriendActionError()
        }
    }

    Scaffold(
        topBar = {
            Text(
                text = stringResource(R.string.friends_top_bar_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            )
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = BottomNavTab.FRIENDS,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomNavTab.HOME -> onNavigateToHome()
                        BottomNavTab.SEARCH -> onNavigateToSearch()
                        BottomNavTab.LIBRARY -> onNavigateToLibrary()
                        BottomNavTab.FRIENDS -> {}
                        BottomNavTab.PROFILE -> onNavigateToProfile()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (sessionUser == null) {
                SignInRequiredState(
                    title = stringResource(R.string.friends_sign_in_required_title),
                    message = stringResource(R.string.friends_sign_in_required_message),
                    buttonText = stringResource(R.string.profile_sign_in),
                    onSignInClick = onNavigateToSignIn
                )
            } else {
                uiState.error?.let { message ->
                    ErrorCard(
                        message = message,
                        onRetry = viewModel::retry,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                FriendsTabContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateToFriendProfile = onNavigateToFriendProfile,
                    onNavigateToDetail = onNavigateToDetail,
                    onRemoveFriend = { friendPendingRemoval = it },
                    onRejectRequest = { requestPendingRejection = it },
                    onCancelSentRequest = { id, name -> sentRequestPendingCancel = id to name }
                )
            }
        }

        friendPendingRemoval?.let { friend ->
            AlertDialog(
                onDismissRequest = { friendPendingRemoval = null },
                title = { Text(stringResource(R.string.profile_remove_friend_dialog_title)) },
                text = { Text(stringResource(R.string.profile_remove_friend_dialog_message, friend.username)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeFriend(friend.friendId)
                        friendPendingRemoval = null
                    }) {
                        Text(stringResource(R.string.profile_remove_confirm), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { friendPendingRemoval = null }) {
                        Text(stringResource(R.string.profile_remove_cancel))
                    }
                }
            )
        }

        sentRequestPendingCancel?.let { (targetId, targetName) ->
            AlertDialog(
                onDismissRequest = { sentRequestPendingCancel = null },
                title = { Text(stringResource(R.string.profile_cancel_request_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.profile_cancel_request_dialog_message,
                            targetName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username)
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.cancelFriendRequest(targetId)
                        sentRequestPendingCancel = null
                    }) {
                        Text(stringResource(R.string.profile_cancel_request), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { sentRequestPendingCancel = null }) {
                        Text(stringResource(R.string.profile_remove_cancel))
                    }
                }
            )
        }

        // Dorada (2. septembar 2026, korisnički zahtev - potvrda pre odbijanja zahteva za
        // prijateljstvo) - isti obrazac kao friendPendingRemoval iznad.
        requestPendingRejection?.let { request ->
            AlertDialog(
                onDismissRequest = { requestPendingRejection = null },
                title = { Text(stringResource(R.string.profile_reject_request_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.profile_reject_request_dialog_message,
                            request.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username)
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.rejectFriendRequest(request.id)
                        requestPendingRejection = null
                    }) {
                        Text(stringResource(R.string.profile_reject_content_description), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { requestPendingRejection = null }) {
                        Text(stringResource(R.string.profile_remove_cancel))
                    }
                }
            )
        }
    }
}
