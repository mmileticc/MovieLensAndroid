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

/** Sadržaj "Podešavanja" stranice pagera - sopstveni LazyColumn (svaka stranica se skroluje nezavisno). */
@Composable
internal fun SettingsTabContent(
    uiState: ProfileUiState,
    sessionUser: SessionUserInfo?,
    viewModel: ProfileViewModel,
    onSignOut: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Faza 7 dorada #4 (korisnička žalba: podaci profila su bili ispisani dvaput - jednom
            // u read-only header-u, pa odmah ispod opet u "Uredi profil" formi sa istim poljima) -
            // spojeno u JEDAN prikaz koji olovka-ikonica prebacuje između read-only i edit stanja
            // (isti obrazac kao u većini appova), umesto stalno vidljive duplirane forme. Zamenjuje
            // raniji par ProfileHeader/SessionIdentityHeader + EditProfileSection na ovom tabu -
            // Prijatelji tab i dalje koristi plain ProfileHeader/SessionIdentityHeader (bez edit
            // afinosti, videti FriendsTabContent).
            EditableProfileHeader(
                uiState = uiState,
                sessionUser = sessionUser,
                onSave = { username, bio, avatar -> viewModel.updateProfile(username, bio, avatar) },
                onDiscardFailedDraft = viewModel::discardFailedProfileDraft
            )
        }
        if (uiState.tasteProfile.isNotEmpty()) {
            item {
                TasteProfileSection(uiState.tasteProfile)
            }
        }
        item {
            // Faza 2 bugfix (16. avgust 2026) - ranije ovaj ceo odeljak (uklj. "Verzija" i "Backend
            // URL" kartice, ne samo "Tamna tema") NIKAD nije bio vidljiv, jer je bio uslovljen sa
            // uiState.userSettings != null, a taj backend poziv nikad nije uspevao (backend nema
            // /settings rutu). "Tamna tema" je sad čisto lokalno podešavanje pa uslov više nije
            // potreban - odeljak se uvek prikazuje.
            SettingsSection(viewModel, onSignOut)
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Izbor jezika aplikacije (oktobar 2026): Sistem (prati jezik telefona) / Srpski / English.
 * Promena se čuva u AppLocale i odmah primenjuje ponovnim kreiranjem aktivnosti. Nazivi jezika
 * su namerno uvek na svom jeziku ("Srpski", "English") - da ih korisnik prepozna i kad trenutni
 * jezik aplikacije ne razume.
 */
@Composable
internal fun LanguageSetting() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val selected = remember { AppLocale.getOption(context) }
    val options = listOf(
        AppLanguageOption.SYSTEM to stringResource(R.string.profile_language_system),
        AppLanguageOption.SERBIAN to "Srpski",
        AppLanguageOption.ENGLISH to "English"
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(R.string.profile_language))
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (option, label) ->
                    SegmentedButton(
                        selected = selected == option,
                        onClick = {
                            if (option != selected) {
                                AppLocale.setOption(context, option)
                                activity?.recreate()
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        icon = {}
                    ) {
                        Text(label, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsSection(
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

        // Faza "settings cleanup" (16. avgust 2026, korisnička žalba) - "Obaveštenja" i "Modus
        // štednje podataka" su bili prikazani ali potpuno neožičeni (onCheckedChange = TODO,
        // switch se vizuelno pomeri ali ništa se ne dešava) - uklonjeni dok stvarno ne zažive.
        // "Tamna tema" je sad STVARNO funkcionalna (Faza 2) - čita/piše lokalno preko
        // ThemePreferences (viewModel.isDarkMode), ne preko backend /settings rute koja nikad
        // nije postojala.
        val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.profile_dark_mode))
                Switch(checked = isDarkMode, onCheckedChange = { viewModel.toggleDarkMode() })
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LanguageSetting()

        Spacer(modifier = Modifier.height(16.dp))
        NotificationSettingsSection()

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

        // Dorada (1. septembar 2026, priprema APK-a za spoljne testere) - ceo "Backend URL" editor
        // (kartica + dijalog) je sad vidljiv samo kad je DevSettings.SHOW_BACKEND_URL_SETTINGS =
        // true (videti obrazloženje tamo zašto NIJE vezano za BuildConfig.DEBUG). Testeri dobijaju
        // `debug`-potpisan APK (radi bez keystore-a) sa ovim flag-om na `false` - ne vide/ne mogu
        // da menjaju backend rutu, ostaju na difoltnoj (Render) iz UrlStorage.
        if (DevSettings.SHOW_BACKEND_URL_SETTINGS) {
            Spacer(modifier = Modifier.height(12.dp))

            // Mini backend URL editor for testing
            val showBackendDialog = remember { mutableStateOf(false) }
            val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()

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
                val savedRoutes by viewModel.savedRoutes.collectAsStateWithLifecycle()
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
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
