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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
                    items(uiState.friends, key = { it.friendId }) { friend ->
                        FriendCard(friend)
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
private fun FriendCard(friend: dev.milinko.movielens.data.remote.user.FriendInfo) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.username, fontWeight = FontWeight.SemiBold)
                Text(
                    "Match: ${String.format(LocalLocale.current.platformLocale, "%.0f", friend.matchScore)}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (friend.commonGenres.isNotEmpty()) {
                    Text(
                        "Žanrovi: ${friend.commonGenres.take(2).joinToString(", ")}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .size(50.dp)
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
        }
    }
}

// Zajednička AppBottomNavigationBar komponenta (ui/components) — bez lokalnih duplikata.

