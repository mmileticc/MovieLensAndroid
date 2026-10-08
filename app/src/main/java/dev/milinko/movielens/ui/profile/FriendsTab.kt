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

/** Sadržaj "Prijatelji" stranice pagera - sopstveni LazyColumn (svaka stranica se skroluje nezavisno). */
@Composable
internal fun FriendsTabContent(
    uiState: ProfileUiState,
    viewModel: ProfileViewModel,
    onNavigateToFriendProfile: (String) -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    onRemoveFriend: (FriendInfo) -> Unit,
    onRejectRequest: (PendingRequest) -> Unit,
    onCancelSentRequest: (id: String, name: String?) -> Unit
) {
    // Bugfix (E1.4, 2. septembar 2026, feedback testera - "lista prijatelja se ne ažurira odmah,
    // nema pull-down") - swipe-down gest kao dodatna sigurnosna mreža uz automatski refresh posle
    // prihvatanja zahteva (videti ProfileViewModel.acceptFriendRequest/refreshFriends).
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshingFriends,
        onRefresh = { viewModel.refreshFriends() },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // UX dorada (oktobar 2026): zaglavlje profila i "Tvoj ukus" su samo na tabu "Moj profil" -
        // ovde su ranije bili duplirani i gurali pretragu ~250dp niže. Dolazni zahtevi su PRVI
        // (ranije ispod pretrage, pa ih je kucanje u pretragu guralo van ekrana).
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
                    onRejectClick = { onRejectRequest(request) },
                    onOpenProfile = { onNavigateToFriendProfile(request.id) }
                )
            }
        }

        item {
            FriendSearchSection(
                query = uiState.friendSearchQuery,
                isSearching = uiState.isSearchingFriends,
                error = uiState.friendSearchError,
                results = uiState.friendSearchResults,
                inFlightIds = uiState.friendActionInFlightIds,
                onQueryChange = viewModel::setFriendSearchQuery,
                onAdd = viewModel::sendFriendRequest,
                onAccept = viewModel::acceptFriendRequest,
                onCancelRequest = onCancelSentRequest,
                onOpenProfile = onNavigateToFriendProfile
            )
        }

        // Korisnički zahtev (2. septembar 2026, "da mi budu vidljivi izlistani poslati zahtevi
        // kao sto su mi izlistani prijatelji, a ne samo da kad ponovo pretrazim osobu vidim da
        // sam mu vec poslao zahtev") - odvojena sekcija, analogno prijateljima/primljenim
        // zahtevima iznad, umesto da se status otkriva samo ponovnom pretragom te osobe.
        if (uiState.sentRequests.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(R.string.profile_sent_requests_header),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            items(uiState.sentRequests, key = { "sent-${it.id}" }) { request ->
                SentRequestCard(
                    request = request,
                    onCancelClick = { onCancelSentRequest(request.id, request.username) },
                    onOpenProfile = { onNavigateToFriendProfile(request.id) }
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
                FriendActivityRow(activity, onClick = { onNavigateToDetail(activity.tmdbId) })
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    }
}

/**
 * Faza 7 redizajna (MOVIELENS_DESIGN_PLAN.md 2.6): "avatar, ime, match%, zajednički žanrovi kao
 * mali chip-ovi". Usput ispravljen postojeći propust - `friend.avatar` polje je postojalo u
 * `FriendInfo` modelu ali ga kartica nikad nije koristila, uvek je prikazivala placeholder
 * ikonicu čak i kad je pravi avatar dostupan.
 */
@Composable
internal fun FriendCard(
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
            // UX dorada (oktobar 2026): "Ukloni" je ranije stajalo kao vidljivo crveno dugme odmah
            // pored klikabilne kartice - lako se promaši. Sad je iza menija (⋮); potvrda ostaje.
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.profile_friend_options_content_description, friend.username)
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.profile_remove_friend_menu), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FriendSearchSection(
    query: String,
    isSearching: Boolean,
    error: String?,
    results: List<UserSearchResult>,
    inFlightIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onAccept: (String) -> Unit,
    onCancelRequest: (id: String, name: String?) -> Unit,
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
                        isActionInFlight = result.id in inFlightIds,
                        onAdd = { onAdd(result.id) },
                        onAccept = { onAccept(result.id) },
                        onCancelRequest = { onCancelRequest(result.id, result.username) },
                        // Bugfix (E1.5, 2. septembar 2026, feedback testera - "nemogućnost pregleda
                        // profila pre slanja zahteva") - RANIJE klik nije radio dok status nije
                        // ACCEPTED. FriendProfileScreen sad prikazuje ograničen preview za bilo koji
                        // status (videti FriendsRouting.kt/FriendProfileScreen.kt), pa je klik uvek
                        // dozvoljen.
                        onClick = { onOpenProfile(result.id) }
                    )
                }
            }
        } else if (error != null && !isSearching) {
            // Pad mreže ne sme da izgleda kao "nema korisnika sa tim imenom".
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
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
internal fun UserSearchResultRow(
    result: UserSearchResult,
    isActionInFlight: Boolean,
    onAdd: () -> Unit,
    onAccept: () -> Unit,
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
            // Bugfix (2. septembar 2026, feedback - "posalji/otkazi rapidno, dodaj posle radi sporo
            // i nikako da osvezi") - dugme se onemogući dok je akcija za ovaj id u letu (videti
            // friendActionInFlightIds/ProfileViewModel) - sprečava rapidno naizmenično
            // slanje/otkazivanje koje je ranije otkazivalo prethodni mrežni poziv usred leta.
            when (result.status) {
                "NONE" -> AppButton(text = stringResource(R.string.profile_add_friend), onClick = onAdd, variant = AppButtonVariant.Primary, enabled = !isActionInFlight)
                "PENDING_SENT" -> TextButton(onClick = onCancelRequest, enabled = !isActionInFlight) {
                    Text(stringResource(R.string.profile_cancel_request), color = MaterialTheme.colorScheme.error)
                }
                // UX dorada (oktobar 2026): ranije onemogućeno dugme "Poslao ti je zahtev" koje je
                // izgledalo pokvareno - sad pravo "Prihvati" (odbijanje i dalje ide preko profila
                // ili kartice zahteva na vrhu taba, gde postoji potvrda).
                "PENDING_RECEIVED" -> AppButton(text = stringResource(R.string.profile_accept_request), onClick = onAccept, variant = AppButtonVariant.Primary, enabled = !isActionInFlight)
                "ACCEPTED" -> AppButton(text = stringResource(R.string.profile_already_friends), onClick = {}, variant = AppButtonVariant.Secondary, enabled = false)
                else -> AppButton(text = stringResource(R.string.profile_add_friend), onClick = onAdd, variant = AppButtonVariant.Primary, enabled = !isActionInFlight)
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
internal fun FriendActivityRow(activity: FriendActivity, onClick: () -> Unit) {
    // Faza B bugfix (16. avgust 2026) - ranije `rating!!` teoretski bezbedno (uslov `isRated`
    // proverava != null gore), ali oslanja se na to da dva mesta (uslov i upotreba) OSTANU
    // sinhronizovana - lako pući ako se logika ikad refaktoriše nezavisno. Sad je `rating` jedna
    // promenljiva koja i JESTE null-provera, nema odvojenog `!!`.
    val rating = activity.metadata.rating?.takeIf { activity.action == "rated" }
    val metaText = if (rating != null) {
        stringResource(R.string.profile_activity_row_rated, activity.friendName, rating.toInt())
    } else {
        stringResource(R.string.profile_activity_row_watched, activity.friendName)
    }

    // Dorada (17. avgust 2026, korisnička žalba "nek i to na klik vodi na taj film") - red je već
    // imao poster preko MovieListRow, samo nije prosleđivan onClick (parametar koji MovieListRow
    // već podržava, isti obrazac kao Friend Profile "odgledano" lista).
    MovieListRow(
        title = activity.movieTitle,
        posterPath = activity.posterPath,
        metaText = metaText,
        // E2.2 dorada (3. septembar 2026) - stari privatan review prikaz uklonjen iz feed-a
        // aktivnosti (zamenjen javnim komentarima na Movie Detail ekranu).
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}
