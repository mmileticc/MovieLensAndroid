package dev.milinko.movielens.ui.profile

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.ui.auth.AuthUiState
import dev.milinko.movielens.ui.auth.AuthViewModel
import dev.milinko.movielens.ui.components.AppBottomNavigationBar
import dev.milinko.movielens.ui.components.BottomNavTab
import dev.milinko.movielens.ui.components.SettingsDialog

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onSignOut: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val sessionUser by viewModel.sessionUser.collectAsState()
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    // quick backend editor visible in header for test flows
    val showBackendDialogGlobal = rememberSaveable { mutableStateOf(false) }

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
                    "Profil",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // quick access to backend editor in top bar
                    TextButton(onClick = { showBackendDialogGlobal.value = true }) {
                        Text("Backend")
                    }
                    if (isSignedIn) {
                        TextButton(
                            onClick = {
                                authViewModel.signOut()
                                onSignOut()
                            }
                        ) {
                            Text("Odjavi se", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(
                            onClick = onNavigateToSignIn
                        ) {
                            Text("Prijavi se", color = MaterialTheme.colorScheme.primary)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            item {
                val fullProfile = uiState.userProfile
                if (fullProfile != null) {
                    // Backend profil (sa statistikom) kad GET /api/users/{id} bude dostupan.
                    ProfileHeader(fullProfile)
                } else if (sessionUser != null) {
                    // Dok taj endpoint ne postoji, prikazujemo bar ono što već imamo lokalno
                    // iz Firebase/backend auth exchange-a (ime, email, avatar) — bez čekanja mreže.
                    SessionIdentityHeader(sessionUser!!)
                }
            }

            item {
                SecondaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Podešavanja") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Prijatelji") }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> {
                    item {
                        if (uiState.userSettings != null) {
                            SettingsSection(uiState.userSettings!!, viewModel, onSignOut)
                        }
                    }
                }
                1 -> {
                    item {
                        FriendSearchSection(
                            query = uiState.friendSearchQuery,
                            isSearching = uiState.isSearchingFriends,
                            results = uiState.friendSearchResults,
                            onQueryChange = viewModel::setFriendSearchQuery,
                            onAdd = viewModel::sendFriendRequest
                        )
                    }

                    if (uiState.pendingRequests.isNotEmpty()) {
                        item {
                            SectionHeader("Zahtevi za prijateljstvo")
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
                        SectionHeader("Prijatelji")
                    }
                    if (uiState.friends.isEmpty()) {
                        item {
                            Text(
                                "Još nemaš prijatelja. Pretraži iznad da dodaš nekoga.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        items(uiState.friends, key = { it.friendId }) { friend ->
                            FriendCard(friend, onRemove = { viewModel.removeFriend(friend.friendId) })
                        }
                    }

                    if (uiState.friendActivity.isNotEmpty()) {
                        item {
                            SectionHeader("Aktivnost prijatelja")
                        }
                        items(uiState.friendActivity, key = { it.activityId }) { activity ->
                            FriendActivityCard(activity)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Backend dialog - renderovan izvan LazyColumn-a
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
        Surface(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            profile.username,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            profile.email,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("${profile.stats.moviesWatched}", "Gledano")
            StatItem("${profile.stats.moviesRated}", "Ocenjeno")
            StatItem("${profile.stats.friendCount}", "Prijatelji")
        }

        if (profile.favoriteGenres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Omiljeni žanrovi: ${profile.favoriteGenres.joinToString(", ")}",
                fontSize = 12.sp,
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
        if (!user.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(user.avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = user.username,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
            )
        } else {
            Surface(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            user.username?.takeIf { it.isNotBlank() } ?: "Korisnik",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        user.email?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            "Postavke aplikacije",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
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
                Text("Tamna tema")
                Switch(
                    checked = settings.darkMode,
                    onCheckedChange = {
                        viewModel.toggleDarkMode()
                    }
                )
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
                Text("Obaveštenja")
                Switch(
                    checked = settings.notifications,
                    onCheckedChange = { /* TODO */ }
                )
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
                Text("Modus štednje podataka")
                Switch(
                    checked = settings.dataSaverMode,
                    onCheckedChange = { /* TODO */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "O aplikaciji",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Verzija")
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
                    Text("Backend URL", fontWeight = FontWeight.Medium)
                    Text(baseUrl, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showBackendDialog.value = true }) {
                    Text("Edit")
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

@Composable
private fun FriendCard(friend: dev.milinko.movielens.data.remote.user.FriendInfo, onRemove: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(friend.username, fontWeight = FontWeight.SemiBold)
                // match_score/common_genres su placeholder (0f/prazno) dok se ne uradi Faza 4
                // (profilisanje) - prikazujemo ih samo kad je backend stvarno popunio vrednosti.
                if (friend.matchScore > 0f) {
                    Text(
                        "Match: ${String.format(LocalLocale.current.platformLocale, "%.0f", friend.matchScore)}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (friend.commonGenres.isNotEmpty()) {
                    Text(
                        "Žanrovi: ${friend.commonGenres.take(2).joinToString(", ")}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onRemove) {
                Text("Ukloni", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun FriendSearchSection(
    query: String,
    isSearching: Boolean,
    results: List<UserSearchResult>,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Pretraži korisnike (ime ili email)") },
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
                    UserSearchResultRow(result = result, onAdd = { onAdd(result.id) })
                }
            }
        } else if (!isSearching && query.trim().length >= 2) {
            Text(
                "Nema rezultata za \"$query\"",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun UserSearchResultRow(result: UserSearchResult, onAdd: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                result.username?.takeIf { it.isNotBlank() } ?: "Korisnik",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            when (result.status) {
                "NONE" -> Button(onClick = onAdd) { Text("Dodaj") }
                "PENDING_SENT" -> OutlinedButton(onClick = {}, enabled = false) { Text("Zahtev poslat") }
                "PENDING_RECEIVED" -> OutlinedButton(onClick = {}, enabled = false) { Text("Odgovori ispod ↓") }
                "ACCEPTED" -> OutlinedButton(onClick = {}, enabled = false) { Text("Već prijatelji") }
                else -> Button(onClick = onAdd) { Text("Dodaj") }
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
                request.username?.takeIf { it.isNotBlank() } ?: "Korisnik",
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = "Prihvati", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onReject) {
                Icon(Icons.Default.Close, contentDescription = "Odbij", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun FriendActivityCard(activity: FriendActivity) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val text = if (activity.action == "rated" && activity.metadata.rating != null) {
                    "${activity.friendName} je ocenio/la \"${activity.movieTitle}\" sa ${activity.metadata.rating.toInt()}/10"
                } else {
                    "${activity.friendName} je odgledao/la \"${activity.movieTitle}\""
                }
                Text(text, fontSize = 13.sp)
            }
            if (activity.action == "rated" && activity.metadata.rating != null) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color(0xFFF5C518),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// Zajednička AppBottomNavigationBar komponenta (ui/components) — bez lokalnih duplikata.

