package dev.milinko.movielens.ui.profile

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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.ui.components.SettingsDialog
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    // quick backend editor visible in header for test flows
    val showBackendDialogGlobal = rememberSaveable { mutableStateOf(false) }
    val baseUrlGlobal by viewModel.baseUrl.collectAsState()

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
        viewModel.loadProfile("user123") // TODO: Get actual userId
    }

    Scaffold(
        topBar = {
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
                // quick access to backend editor in top bar
                TextButton(onClick = { showBackendDialogGlobal.value = true }) {
                    Text("Backend")
                }
            }
        },
        bottomBar = {
            BottomNavigationBar(
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
                if (uiState.userProfile != null) {
                    ProfileHeader(uiState.userProfile!!)
                }

                if (showBackendDialogGlobal.value) {
                    SettingsDialog(
                        currentUrl = baseUrlGlobal,
                        onDismiss = { showBackendDialogGlobal.value = false },
                        onSave = {
                            viewModel.updateBaseUrl(it)
                            showBackendDialogGlobal.value = false
                        }
                    )
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
                        viewModel.toggleDarkMode("user123")
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
                androidx.compose.material3.TextButton(onClick = { showBackendDialog.value = true }) {
                    Text("Edit")
                }
            }
        }

        if (showBackendDialog.value) {
            SettingsDialog(
                currentUrl = baseUrl,
                onDismiss = { showBackendDialog.value = false },
                onSave = {
                    viewModel.updateBaseUrl(it)
                    showBackendDialog.value = false
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign-out button
        val authViewModel: dev.milinko.movielens.ui.auth.AuthViewModel = androidx.hilt.navigation.compose.hiltViewModel()
        androidx.compose.material3.Button(
            onClick = {
                authViewModel.signOut()
                onSignOut()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text("Odjavi se", fontWeight = FontWeight.SemiBold)
        }
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

enum class BottomNavTab {
    HOME, SEARCH, LIBRARY, PROFILE
}

@Composable
fun BottomNavigationBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem("Home", Icons.Default.Search, selectedTab == BottomNavTab.HOME) {
                onTabSelected(BottomNavTab.HOME)
            }
            BottomNavItem("Pretraga", Icons.Default.Search, selectedTab == BottomNavTab.SEARCH) {
                onTabSelected(BottomNavTab.SEARCH)
            }
            BottomNavItem("Biblioteka", Icons.AutoMirrored.Filled.LibraryBooks, selectedTab == BottomNavTab.LIBRARY) {
                onTabSelected(BottomNavTab.LIBRARY)
            }
            BottomNavItem("Profil", Icons.Default.Settings, selectedTab == BottomNavTab.PROFILE) {
                onTabSelected(BottomNavTab.PROFILE)
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



