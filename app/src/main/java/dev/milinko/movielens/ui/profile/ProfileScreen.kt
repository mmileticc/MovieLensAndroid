package dev.milinko.movielens.ui.profile

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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
import dev.milinko.movielens.ui.components.ExpandableMatchCard
import dev.milinko.movielens.ui.components.GenreChip
import dev.milinko.movielens.ui.components.MovieListRow
import dev.milinko.movielens.ui.components.RemoteAvatarImage
import dev.milinko.movielens.ui.components.SectionHeader
import dev.milinko.movielens.ui.components.SettingsDialog
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
    onNavigateToFriendProfile: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionUser by viewModel.sessionUser.collectAsState()
    // quick backend editor visible in header for test flows
    val showBackendDialogGlobal = rememberSaveable { mutableStateOf(false) }
    // Potvrda pre nego što se prijatelj stvarno ukloni - nema pravog "undo" jer bi ponovno
    // dodavanje zahtevalo da druga strana opet prihvati zahtev (nije simetrično kao watched-undo).
    var friendPendingRemoval by remember { mutableStateOf<FriendInfo?>(null) }

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
            val authUiState by authViewModel.uiState.collectAsState()
            val isSignedIn = authUiState is AuthUiState.SignedIn || authViewModel.isAlreadySignedIn()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.profile_top_bar_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // quick access to backend editor in top bar
                    TextButton(onClick = { showBackendDialogGlobal.value = true }) {
                        Text(stringResource(R.string.profile_backend_button))
                    }
                    if (isSignedIn) {
                        TextButton(
                            onClick = {
                                authViewModel.signOut()
                                onSignOut()
                            }
                        ) {
                            Text(stringResource(R.string.profile_sign_out), color = MaterialTheme.colorScheme.error)
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
                        BottomNavTab.PROFILE -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        // Svajp između tabova (pored klika na naslov kartice) - HorizontalPager za sadržaj ispod
        // tab reda, dok header (avatar, taste profile) i sam tab red ostaju fiksni iznad. Tab
        // indikator boja: videti napomenu u LibraryScreen.kt - nasleđuje colorScheme.primary
        // od Faze 1, bez potrebe za custom indicator kodom.
        val pagerState = rememberPagerState(pageCount = { 2 })
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val fullProfile = uiState.userProfile
            if (fullProfile != null) {
                // Backend profil (sa statistikom) kad GET /api/users/{id} bude dostupan.
                ProfileHeader(fullProfile)
            } else if (sessionUser != null) {
                // Dok taj endpoint ne postoji, prikazujemo bar ono što već imamo lokalno
                // iz Firebase/backend auth exchange-a (ime, email, avatar) — bez čekanja mreže.
                SessionIdentityHeader(sessionUser!!)
            }

            if (uiState.tasteProfile.isNotEmpty()) {
                TasteProfileSection(uiState.tasteProfile)
            }

            SecondaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.profile_tab_settings)) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.profile_tab_friends)) }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> SettingsTabContent(uiState, sessionUser, viewModel, onSignOut)
                    1 -> FriendsTabContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToFriendProfile = onNavigateToFriendProfile,
                        onRemoveFriend = { friendPendingRemoval = it }
                    )
                }
            }
        }

        // Backend dialog - renderovan izvan Column-a
        if (showBackendDialogGlobal.value) {
            val savedRoutes by viewModel.savedRoutes.collectAsState()
            val baseUrl by viewModel.baseUrl.collectAsState()
            SettingsDialog(
                currentUrl = baseUrl,
                savedRoutes = savedRoutes,
                onDismiss = { showBackendDialogGlobal.value = false },
                onSave = {
                    viewModel.updateBaseUrl(it)
                    showBackendDialogGlobal.value = false
                },
                onAddRoute = { viewModel.addSavedRoute(it) },
                onDeleteRoute = { viewModel.removeSavedRoute(it) }
            )
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
    }
}

/** Sadržaj "Podešavanja" stranice pagera - sopstveni LazyColumn (svaka stranica se skroluje nezavisno). */
@Composable
private fun SettingsTabContent(
    uiState: ProfileUiState,
    sessionUser: SessionUserInfo?,
    viewModel: ProfileViewModel,
    onSignOut: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Faza 7 dorada (korisnička žalba: "u podešavanjima nema ništa") - uređivanje profila
            // je sad glavni sadržaj ovog taba, iznad ostalih postavki.
            EditProfileSection(
                uiState = uiState,
                sessionUser = sessionUser,
                onSave = { username, bio, avatar -> viewModel.updateProfile(username, bio, avatar) }
            )
        }
        item {
            if (uiState.userSettings != null) {
                SettingsSection(uiState.userSettings!!, viewModel, onSignOut)
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Sadržaj "Prijatelji" stranice pagera - sopstveni LazyColumn (svaka stranica se skroluje nezavisno). */
@Composable
private fun FriendsTabContent(
    uiState: ProfileUiState,
    viewModel: ProfileViewModel,
    onNavigateToFriendProfile: (String) -> Unit,
    onRemoveFriend: (FriendInfo) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            FriendSearchSection(
                query = uiState.friendSearchQuery,
                isSearching = uiState.isSearchingFriends,
                results = uiState.friendSearchResults,
                onQueryChange = viewModel::setFriendSearchQuery,
                onAdd = viewModel::sendFriendRequest,
                onCancelRequest = viewModel::cancelFriendRequest,
                onOpenProfile = onNavigateToFriendProfile
            )
        }

        if (uiState.pendingRequests.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.profile_friend_requests_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(uiState.pendingRequests, key = { "pending-${it.id}" }) { request ->
                PendingRequestCard(
                    request = request,
                    onAccept = { viewModel.acceptFriendRequest(request.id) },
                    onReject = { viewModel.rejectFriendRequest(request.id) }
                )
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.profile_friends_header),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        if (uiState.isLoading && uiState.friends.isEmpty()) {
            // Dok se prvi ikad poziv ne vrati, ne pokazujemo "nemaš prijatelja" - to bi bilo
            // lažno ako korisnik zapravo ima prijatelje, samo se lista još nije stigla da učita
            // (npr. brz prelazak/svajp na ovaj tab).
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        } else if (uiState.friends.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.profile_friends_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        } else {
            items(uiState.friends, key = { it.friendId }) { friend ->
                FriendCard(
                    friend,
                    onClick = { onNavigateToFriendProfile(friend.friendId) },
                    onRemove = { onRemoveFriend(friend) }
                )
            }
        }

        if (uiState.friendActivity.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.profile_friend_activity_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(uiState.friendActivity, key = { it.activityId }) { activity ->
                FriendActivityRow(activity)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Krug avatara sa tankim `accentPrimary` obrubom (Faza 7, MOVIELENS_DESIGN_PLAN.md 2.6) - deljen
 * između `ProfileHeader`/`SessionIdentityHeader` (sopstveni profil) i `FriendProfileScreen`
 * (profil prijatelja, "isti stil avatara"). */
@Composable
private fun BorderedAvatar(avatarUrl: String?, fallbackLabel: String?, size: Int = 80) {
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

@Composable
private fun ProfileHeader(profile: dev.milinko.movielens.data.remote.user.UserProfile) {
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

@Composable
private fun SessionIdentityHeader(user: SessionUserInfo) {
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

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Uređivanje profila (Faza 7 dorada) - korisničko ime (jedinstveno kao Instagram handle, videti
 * `UserDao.updateProfile` na backendu) i kratak "o meni" opis. `username`/`bio` polja se
 * inicijalizuju iz `uiState.userProfile` (ili session fallback-a dok se profil ne učita) i drže se
 * u lokalnom stanju dok korisnik kuca - `remember(uiState.userProfile?.userId)` ih resetuje samo
 * kad se prvi put učita profil drugog korisnika (u praksi: nikad, ovo je uvek sopstveni profil),
 * ne pri svakom osvežavanju posle uspešnog čuvanja.
 */
@Composable
private fun EditProfileSection(
    uiState: ProfileUiState,
    sessionUser: SessionUserInfo?,
    onSave: (username: String, bio: String?, newAvatarDataUri: String?) -> Unit
) {
    val initialUsername = uiState.userProfile?.username ?: sessionUser?.username ?: ""
    val initialBio = uiState.userProfile?.bio ?: ""
    val initialAvatar = uiState.userProfile?.avatar ?: sessionUser?.avatarUrl
    var username by remember(uiState.userProfile?.userId) { mutableStateOf(initialUsername) }
    var bio by remember(uiState.userProfile?.userId) { mutableStateOf(initialBio) }

    // Slika izabrana OVOG puta (data URI, već kompresovana) - null dok korisnik ne izabere novu
    // sliku, u kom slučaju se šalje trenutna (initialAvatar) nepromenjena (videti ViewModel.updateProfile).
    var pickedAvatarDataUri by remember(uiState.userProfile?.userId) { mutableStateOf<String?>(null) }
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_edit_section_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 16.dp)
        ) {
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

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_username_label)) },
            supportingText = { Text(stringResource(R.string.profile_username_helper)) },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { if (it.length <= 160) bio = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_bio_label)) },
            supportingText = { Text(stringResource(R.string.profile_bio_helper, bio.length)) },
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

        AppButton(
            text = stringResource(
                if (uiState.isSavingProfile) R.string.profile_saving else R.string.profile_save_changes
            ),
            onClick = { onSave(username.trim(), bio.trim().ifBlank { null }, pickedAvatarDataUri) },
            enabled = !uiState.isSavingProfile && !isCompressingAvatar && username.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun SettingsSection(
    settings: dev.milinko.movielens.data.remote.user.UserSettings,
    viewModel: ProfileViewModel,
    onSignOut: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.profile_settings_app_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.profile_dark_mode))
                Switch(checked = settings.darkMode, onCheckedChange = { viewModel.toggleDarkMode() })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.profile_notifications))
                Switch(checked = settings.notifications, onCheckedChange = { /* TODO */ })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.profile_data_saver))
                Switch(checked = settings.dataSaverMode, onCheckedChange = { /* TODO */ })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.profile_about_section),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.profile_version_label))
                Text("1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Mini backend URL editor for testing
        val showBackendDialog = remember { mutableStateOf(false) }
        val baseUrl by viewModel.baseUrl.collectAsState()

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.profile_backend_url_label), style = MaterialTheme.typography.titleSmall)
                    Text(baseUrl, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showBackendDialog.value = true }) {
                    Text(stringResource(R.string.profile_backend_edit))
                }
            }
        }

        if (showBackendDialog.value) {
            val savedRoutes by viewModel.savedRoutes.collectAsState()
            SettingsDialog(
                currentUrl = baseUrl,
                savedRoutes = savedRoutes,
                onDismiss = { showBackendDialog.value = false },
                onSave = {
                    viewModel.updateBaseUrl(it)
                    showBackendDialog.value = false
                },
                onAddRoute = { viewModel.addSavedRoute(it) },
                onDeleteRoute = { viewModel.removeSavedRoute(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Faza 7 redizajna (MOVIELENS_DESIGN_PLAN.md 2.6): "avatar, ime, match%, zajednički žanrovi kao
 * mali chip-ovi". Usput ispravljen postojeći propust - `friend.avatar` polje je postojalo u
 * `FriendInfo` modelu ali ga kartica nikad nije koristila, uvek je prikazivala placeholder
 * ikonicu čak i kad je pravi avatar dostupan.
 */
@Composable
private fun FriendCard(
    friend: FriendInfo,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape)) {
                if (!friend.avatar.isNullOrBlank()) {
                    RemoteAvatarImage(
                        url = friend.avatar,
                        contentDescription = friend.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = friend.username,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(friend.username, style = MaterialTheme.typography.titleSmall)
                // match_score/common_genres su placeholder (0f/prazno) dok se ne uradi Faza 4
                // (profilisanje) - prikazujemo ih samo kad je backend stvarno popunio vrednosti.
                if (friend.matchScore > 0f) {
                    Text(
                        text = stringResource(R.string.profile_match_percent, friend.matchScore),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (friend.commonGenres.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        friend.commonGenres.take(2).forEach { genre ->
                            GenreChip(text = genre)
                        }
                    }
                }
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.profile_remove_confirm), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * "Tvoj ukus" - prosečna ocena po žanru (Faza 4, MOVIELENS_ROADMAP.md). Faza 7 dorada (korisnička
 * žalba #1: sekcija je kao vertikalna lista od 5 kartica zauzimala previše prostora iznad tabova i
 * gurala Prijatelje/Aktivnost duboko dole) - prebačeno u kompaktan horizontalni red (top 3 žanra,
 * `LazyRow`). Dorada #2 (korisnik je tražio da i dalje može da vidi SVE žanrove, ne samo top 3):
 * ostatak liste (`sorted.drop(3)`) je sad iza `ExpandableMatchCard` "Prikaži još N žanrova" -
 * isti sklopivi obrazac kao showtimes na Movie Detail-u i sekundarni pogoci na Search-u, u
 * originalnom vertikalnom stilu (žanr + ocena + traka po redu) kad se razvije.
 */
@Composable
private fun TasteProfileSection(genres: List<GenreAffinity>) {
    val sorted = remember(genres) { genres.sortedByDescending { it.averageRating } }
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
                                            String.format(java.util.Locale.getDefault(), "%.1f", affinity.averageRating),
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

@Composable
private fun CompactGenreAffinityCard(affinity: GenreAffinity) {
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
                String.format(java.util.Locale.getDefault(), "%.1f", affinity.averageRating),
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

@Composable
private fun FriendSearchSection(
    query: String,
    isSearching: Boolean,
    results: List<UserSearchResult>,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onCancelRequest: (String) -> Unit,
    onOpenProfile: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.profile_search_users_label)) },
            singleLine = true,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                }
            }
        )

        if (results.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                results.forEach { result ->
                    UserSearchResultRow(
                        result = result,
                        onAdd = { onAdd(result.id) },
                        onCancelRequest = { onCancelRequest(result.id) },
                        onClick = { if (result.status == "ACCEPTED") onOpenProfile(result.id) }
                    )
                }
            }
        } else if (!isSearching && query.trim().length >= 2) {
            Text(
                text = stringResource(R.string.profile_search_no_results, query),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun UserSearchResultRow(
    result: UserSearchResult,
    onAdd: () -> Unit,
    onCancelRequest: () -> Unit,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            when (result.status) {
                "NONE" -> AppButton(text = stringResource(R.string.profile_add_friend), onClick = onAdd, variant = AppButtonVariant.Primary)
                "PENDING_SENT" -> TextButton(onClick = onCancelRequest) {
                    Text(stringResource(R.string.profile_cancel_request), color = MaterialTheme.colorScheme.error)
                }
                "PENDING_RECEIVED" -> AppButton(text = stringResource(R.string.profile_reply_below), onClick = {}, variant = AppButtonVariant.Secondary, enabled = false)
                "ACCEPTED" -> AppButton(text = stringResource(R.string.profile_already_friends), onClick = {}, variant = AppButtonVariant.Secondary, enabled = false)
                else -> AppButton(text = stringResource(R.string.profile_add_friend), onClick = onAdd, variant = AppButtonVariant.Primary)
            }
        }
    }
}

@Composable
private fun PendingRequestCard(request: PendingRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = request.username?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_default_username),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.profile_accept_content_description), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onReject) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.profile_reject_content_description), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Faza 7 dorada #2 (korisnička žalba: prvi pokušaj - ikonica + rečenica - i dalje je izgledao kao
 * "samo pobrojano"). Prebačeno na deljeni `MovieListRow` (isti poster-red obrazac kao Library/
 * Search/Movie Night/Friend Profile) - film je sad glavni vizuelni element (poster + naslov), a
 * "ko i šta" ide kao `description` ispod, umesto da cela stvar bude jedna generička rečenica sa
 * ikonicom. `FriendActivityDto`/`FriendActivity` su dobili `posterPath` (backend red ga je već
 * imao iz JOIN-a na `movies`, samo nije bio prosleđen kroz DTO).
 */
@Composable
private fun FriendActivityRow(activity: FriendActivity) {
    val isRated = activity.action == "rated" && activity.metadata.rating != null
    val description = if (isRated) {
        stringResource(R.string.profile_activity_row_rated, activity.friendName, activity.metadata.rating!!.toInt())
    } else {
        stringResource(R.string.profile_activity_row_watched, activity.friendName)
    }

    MovieListRow(
        title = activity.movieTitle,
        posterPath = activity.posterPath,
        description = description,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

// Zajednička AppBottomNavigationBar/SectionHeader komponenta (ui/components) — bez lokalnih
// duplikata (ranije je ovaj fajl imao sopstveni privatni SectionHeader, identičan po nameni).
