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


@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onSignOut: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessionUser by viewModel.sessionUser.collectAsStateWithLifecycle()
    // Odjava briše ceo back stack i vraća na prijavu - lako se slučajno pritisne u gornjoj traci.
    var showSignOutConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Collect one-shot UI events (toasts) from ViewModel
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
            val authViewModel: AuthViewModel = hiltViewModel()
            val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
            val isSignedIn = authUiState is AuthUiState.SignedIn || authViewModel.isAlreadySignedIn()

            // Bug fix (17. avgust 2026): dodat statusBarsPadding (videti identičan komentar u
            // LibraryScreen.kt) + uklonjeno duplirano "Backend" dugme koje je otvaralo isti
            // SettingsDialog kao kartica "Backend URL" na Podešavanja tabu (SettingsSection
            // ispod) - dve odvojene tačke ulaza za istu funkciju je nepotrebno zauzimalo top bar
            // i vizuelno zatrpavalo prostor pored Odjavi se/Prijavi se dugmeta. Uređivanje
            // backend URL-a i dalje postoji, samo na jednom mestu (u tabu).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.profile_top_bar_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSignedIn) {
                        TextButton(onClick = { showSignOutConfirm = true }) {
                            Text(stringResource(R.string.profile_sign_out), color = MaterialTheme.colorScheme.error)
                        }
                        if (showSignOutConfirm) {
                            AlertDialog(
                                onDismissRequest = { showSignOutConfirm = false },
                                title = { Text(stringResource(R.string.profile_sign_out_dialog_title)) },
                                text = { Text(stringResource(R.string.profile_sign_out_dialog_message)) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showSignOutConfirm = false
                                        authViewModel.signOut()
                                        onSignOut()
                                    }) {
                                        Text(stringResource(R.string.profile_sign_out), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSignOutConfirm = false }) {
                                        Text(stringResource(R.string.profile_remove_cancel))
                                    }
                                }
                            )
                        }
                    } else {
                        TextButton(onClick = onNavigateToSignIn) {
                            Text(stringResource(R.string.profile_sign_in), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        bottomBar = {
            AppBottomNavigationBar(
                selectedTab = BottomNavTab.PROFILE,
                onTabSelected = { tab ->
                    when (tab) {
                        BottomNavTab.HOME -> onNavigateToHome()
                        BottomNavTab.SEARCH -> onNavigateToSearch()
                        BottomNavTab.LIBRARY -> onNavigateToLibrary()
                        BottomNavTab.FRIENDS -> onNavigateToFriends()
                        BottomNavTab.PROFILE -> {}
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
                // Bugfix (17. avgust 2026, korisnička žalba: neulogovan korisnik je ovde ranije
                // video poluprazan/"pokvaren" EditableProfileHeader - bez username-a, bez
                // avatara, ništa se ne može ni sačuvati bez tokena, izgledalo je kao neki
                // generički nefunkcionalan korisnik umesto jasne poruke). Isti deljeni
                // SignInRequiredState prikaz kao Library kad tab zahteva prijavu - ne navigira
                // automatski, samo jasno kaže šta fali i nudi dugme.
                SignInRequiredState(
                    title = stringResource(R.string.profile_sign_in_required_title),
                    message = stringResource(R.string.profile_sign_in_required_message),
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
                SettingsTabContent(uiState, sessionUser, viewModel, onSignOut)
            }
        }
    }
}
