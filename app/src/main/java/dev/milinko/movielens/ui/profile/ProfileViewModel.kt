package dev.milinko.movielens.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.data.remote.user.UserSettings
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val userSettings: UserSettings? = null,
    val friends: List<FriendInfo> = emptyList(),
    val friendActivity: List<FriendActivity> = emptyList(),
    val pendingRequests: List<PendingRequest> = emptyList(),
    // Search-as-you-type za dodavanje prijatelja (isti obrazac kao SearchViewModel za filmove).
    val friendSearchQuery: String = "",
    val friendSearchResults: List<UserSearchResult> = emptyList(),
    val isSearchingFriends: Boolean = false,
    val friendActionError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Osnovni identitet ulogovanog korisnika, čuvan lokalno u SessionManager-u odmah nakon
 * Firebase/backend auth exchange-a (vidi AuthViewModel.finishGoogleSignIn). Ne zavisi od
 * GET /api/users/{id} (koji jos ne postoji na backendu) - prikazuje se odmah, bez čekanja mreže.
 */
data class SessionUserInfo(
    val username: String?,
    val email: String?,
    val avatarUrl: String?
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val urlStorage: UrlStorage,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _sessionUser = MutableStateFlow(readSessionUser())
    val sessionUser: StateFlow<SessionUserInfo?> = _sessionUser.asStateFlow()

    // Expose base URL from UrlStorage so UI can edit backend address during testing
    private val _baseUrl = MutableStateFlow(urlStorage.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _savedRoutes = MutableStateFlow(urlStorage.getSavedRoutes())
    val savedRoutes: StateFlow<List<String>> = _savedRoutes.asStateFlow()

    private fun readSessionUser(): SessionUserInfo? {
        val userId = sessionManager.getUserId() ?: return null
        return SessionUserInfo(
            username = sessionManager.getUsername(),
            email = sessionManager.getEmail(),
            avatarUrl = sessionManager.getAvatarUrl()
        )
    }

    /** Poziva se pri ponovnom ulasku na Profile ekran (npr. nakon prijave/odjave). */
    fun refreshSessionUser() {
        _sessionUser.value = readSessionUser()
    }

    // One-shot UI events (toasts, messages)
    private val _events = MutableSharedFlow<ProfileUiEvent>()
    val events = _events.asSharedFlow()

    sealed class ProfileUiEvent {
        data class ShowMessage(val message: String) : ProfileUiEvent()
    }

    // Debounce posao za pretragu prijatelja dok korisnik kuca - isti obrazac kao SearchViewModel.
    private var friendSearchJob: Job? = null

    private companion object {
        const val FRIEND_SEARCH_DEBOUNCE_MS = 300L
        const val MIN_FRIEND_QUERY_LENGTH = 2
    }

    fun setFriendSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(friendSearchQuery = query)

        friendSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_FRIEND_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(friendSearchResults = emptyList(), isSearchingFriends = false)
            return
        }

        friendSearchJob = viewModelScope.launch {
            delay(FRIEND_SEARCH_DEBOUNCE_MS)
            _uiState.value = _uiState.value.copy(isSearchingFriends = true)
            userRepository.searchUsers(trimmed)
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(isSearchingFriends = false, friendSearchResults = results)
                }
                .onFailure {
                    // Tiho - CancellationException (otkazana pretraga zbog novog kucanja) se ne
                    // prikazuje kao greška, isti razlog kao kod SearchViewModel/safeApiCall.
                    _uiState.value = _uiState.value.copy(isSearchingFriends = false)
                }
        }
    }

    fun clearFriendSearch() {
        friendSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(friendSearchQuery = "", friendSearchResults = emptyList(), isSearchingFriends = false)
    }

    /**
     * Otkazuje već poslat zahtev (PENDING_SENT -> NONE u rezultatima pretrage). Backend nema
     * poseban "cancel" - isti DELETE endpoint koji se koristi za unfriend/reject briše red
     * bez obzira na smer/status, pa radi i ovde.
     */
    fun cancelFriendRequest(targetUserId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousResults = _uiState.value.friendSearchResults
        _uiState.value = _uiState.value.copy(
            friendSearchResults = previousResults.map { if (it.id == targetUserId) it.copy(status = "NONE") else it }
        )
        viewModelScope.launch {
            userRepository.removeFriend(userId, targetUserId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friendSearchResults = previousResults,
                    friendActionError = e.message ?: "Greška pri otkazivanju zahteva"
                )
            }
        }
    }

    /** Optimistički - status rezultata pretrage se odmah menja u PENDING_SENT, vraća se ako padne. */
    fun sendFriendRequest(targetUserId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousResults = _uiState.value.friendSearchResults
        _uiState.value = _uiState.value.copy(
            friendSearchResults = previousResults.map { if (it.id == targetUserId) it.copy(status = "PENDING_SENT") else it }
        )
        viewModelScope.launch {
            userRepository.addFriend(userId, targetUserId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friendSearchResults = previousResults,
                    friendActionError = e.message ?: "Greška pri slanju zahteva za prijateljstvo"
                )
            }
        }
    }

    /** Optimistički uklanja zahtev iz liste na klik, prihvatanje osvežava listu prijatelja. */
    fun acceptFriendRequest(requesterId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousPending = _uiState.value.pendingRequests
        _uiState.value = _uiState.value.copy(pendingRequests = previousPending.filterNot { it.id == requesterId })
        viewModelScope.launch {
            userRepository.acceptFriendRequest(userId, requesterId)
                .onSuccess {
                    userRepository.getFriends(userId).onSuccess { friends ->
                        _uiState.value = _uiState.value.copy(friends = friends)
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        pendingRequests = previousPending,
                        friendActionError = e.message ?: "Greška pri prihvatanju zahteva"
                    )
                }
        }
    }

    fun rejectFriendRequest(requesterId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousPending = _uiState.value.pendingRequests
        _uiState.value = _uiState.value.copy(pendingRequests = previousPending.filterNot { it.id == requesterId })
        viewModelScope.launch {
            userRepository.rejectFriendRequest(userId, requesterId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    pendingRequests = previousPending,
                    friendActionError = e.message ?: "Greška pri odbijanju zahteva"
                )
            }
        }
    }

    fun removeFriend(friendId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousFriends = _uiState.value.friends
        _uiState.value = _uiState.value.copy(friends = previousFriends.filterNot { it.friendId == friendId })
        viewModelScope.launch {
            userRepository.removeFriend(userId, friendId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friends = previousFriends,
                    friendActionError = e.message ?: "Greška pri uklanjanju prijatelja"
                )
            }
        }
    }

    fun consumeFriendActionError() {
        _uiState.value = _uiState.value.copy(friendActionError = null)
    }

    private fun refreshRoutesState() {
        _baseUrl.value = urlStorage.getBaseUrl()
        _savedRoutes.value = urlStorage.getSavedRoutes()
    }

    fun updateBaseUrl(newUrl: String) {
        viewModelScope.launch {
            try {
                urlStorage.setBaseUrl(newUrl)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Backend URL sačuvan."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage("Greška pri čuvanju URL-a: ${e.message}"))
            }
        }
    }

    fun addSavedRoute(url: String) {
        viewModelScope.launch {
            try {
                urlStorage.addRoute(url)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Ruta je dodata u listu."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage("Greška pri dodavanju rute: ${e.message}"))
            }
        }
    }

    fun removeSavedRoute(url: String) {
        viewModelScope.launch {
            try {
                urlStorage.removeRoute(url)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Ruta je obrisana."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage("Greška pri brisanju rute: ${e.message}"))
            }
        }
    }

    /** Učitava profil trenutno ulogovanog korisnika (iz SessionManager-a). */
    fun loadProfile() {
        refreshSessionUser()
        val userId = sessionManager.getUserId() ?: return

        // avoid reloading the same profile repeatedly
        if (_uiState.value.userProfile?.userId == userId) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Svih pet poziva su nezavisna - paralelno umesto sekvencijalno, inače se vreme
                // čekanja sabira (5x round-trip umesto 1x).
                val profileDeferred = async { userRepository.getUserProfile(userId) }
                val settingsDeferred = async { userRepository.getUserSettings(userId) }
                val friendsDeferred = async { userRepository.getFriends(userId) }
                val activityDeferred = async { userRepository.getFriendActivity(userId) }
                val pendingDeferred = async { userRepository.getPendingFriendRequests(userId) }

                val profileResult = profileDeferred.await()
                val settingsResult = settingsDeferred.await()
                val friendsResult = friendsDeferred.await()
                val activityResult = activityDeferred.await()
                val pendingResult = pendingDeferred.await()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    userProfile = profileResult.getOrNull() ?: _uiState.value.userProfile,
                    userSettings = settingsResult.getOrNull() ?: _uiState.value.userSettings,
                    friends = friendsResult.getOrNull() ?: _uiState.value.friends,
                    friendActivity = activityResult.getOrNull() ?: _uiState.value.friendActivity,
                    pendingRequests = pendingResult.getOrNull() ?: _uiState.value.pendingRequests
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Greška pri učitavanju profila")
            }
        }
    }

    fun updateSettings(userId: String, settings: UserSettings) {
        viewModelScope.launch {
            userRepository.updateUserSettings(userId, settings)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(userSettings = it)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message ?: "Greška pri ažuriranju postavki")
                }
        }
    }

    fun toggleDarkMode() {
        val userId = sessionManager.getUserId() ?: return
        val currentSettings = _uiState.value.userSettings
        if (currentSettings != null) {
            val updated = currentSettings.copy(darkMode = !currentSettings.darkMode)
            updateSettings(userId, updated)
        }
    }
}
