package dev.milinko.movielens.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.pref.ThemePreferences
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.GenreAffinity
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSearchResult
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State shared by the Profile and Friends screens. */
data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val friends: List<FriendInfo> = emptyList(),
    val friendActivity: List<FriendActivity> = emptyList(),
    val pendingRequests: List<PendingRequest> = emptyList(),
    // Sent (outgoing) friend requests, shown in their own section.
    val sentRequests: List<PendingRequest> = emptyList(),
    // Taste profile - top genres by average rating, derived from the user's ratings.
    val tasteProfile: List<GenreAffinity> = emptyList(),
    // Search as you type for adding friends (same pattern as SearchViewModel for movies).
    val friendSearchQuery: String = "",
    val friendSearchResults: List<UserSearchResult> = emptyList(),
    val isSearchingFriends: Boolean = false,
    // A failed user search - without it a network failure looked like "no results for X".
    val friendSearchError: String? = null,
    val friendActionError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Profile editing (username/bio/avatar).
    val isSavingProfile: Boolean = false,
    val editProfileError: String? = null,
    // Input the server rejected - the form reopens with it (see updateProfile).
    val failedProfileDraft: ProfileDraft? = null,
    // Pull-to-refresh on the Friends tab - separate from `isLoading` (the very first load).
    val isRefreshingFriends: Boolean = false,
    // Ids of users with a friend request action in flight. While an id is here its button is
    // disabled (see UserSearchResultRow), so the user cannot start the OPPOSITE action (e.g.
    // cancel) while the previous one (send) is still on its way (see FriendProfileViewModel for why
    // calls are never cancelled mid-flight).
    val friendActionInFlightIds: Set<String> = emptySet()
)

/** Values from the profile edit form that the server rejected. */
data class ProfileDraft(
    val username: String,
    val bio: String?,
    val avatarDataUri: String?
)

/**
 * Basic identity of the signed-in user, stored locally in SessionManager right after the sign-in
 * exchange (see AuthViewModel.finishGoogleSignIn), so it can be shown immediately without waiting
 * for the network.
 */
data class SessionUserInfo(
    val username: String?,
    val email: String?,
    val avatarUrl: String?
)

/**
 * Own profile, settings and all friend features (requests, search, friend list, activity feed),
 * shared by the Profile and Friends screens. Data is shown from the Room cache first and refreshed
 * from the network; friend actions are optimistic and written through to the cache.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val urlStorage: UrlStorage,
    private val sessionManager: SessionManager,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // A local setting (see ThemePreferences.kt).
    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode

    private val _sessionUser = MutableStateFlow(readSessionUser())
    val sessionUser: StateFlow<SessionUserInfo?> = _sessionUser.asStateFlow()

    // Backend URL from UrlStorage, editable in the developer settings (debug builds).
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

    /** Called when the Profile screen is entered again (e.g. after signing in or out). */
    fun refreshSessionUser() {
        _sessionUser.value = readSessionUser()
    }

    // One-shot UI events (toasts, messages).
    private val _events = MutableSharedFlow<ProfileUiEvent>()
    val events = _events.asSharedFlow()

/** One-shot events for the Profile/Friends screens. */
    sealed class ProfileUiEvent {
        data class ShowMessage(val message: String) : ProfileUiEvent()
    }

    // Debounce job for the friend search while the user types - same pattern as SearchViewModel.
    private var friendSearchJob: Job? = null

    // Guard against double taps for accept/reject, keyed by requesterId (send/cancel use
    // friendActionInFlightIds in ProfileUiState).
    private val pendingActionJobs = mutableMapOf<String, Job>()

    private companion object {
        const val FRIEND_SEARCH_DEBOUNCE_MS = 300L
        const val MIN_FRIEND_QUERY_LENGTH = 2
    }

    /** Updates the search query and runs the search after a short debounce. */
    fun setFriendSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(friendSearchQuery = query)

        friendSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_FRIEND_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(friendSearchResults = emptyList(), isSearchingFriends = false, friendSearchError = null)
            return
        }

        friendSearchJob = viewModelScope.launch {
            delay(FRIEND_SEARCH_DEBOUNCE_MS)
            _uiState.value = _uiState.value.copy(isSearchingFriends = true, friendSearchError = null)
            userRepository.searchUsers(trimmed)
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(isSearchingFriends = false, friendSearchResults = results)
                }
                .onFailure { e ->
                    // A CancellationException (search cancelled by new typing) never gets here -
                    // safeApiCall rethrows it; this is a real error (network/server).
                    _uiState.value = _uiState.value.copy(
                        isSearchingFriends = false,
                        friendSearchResults = emptyList(),
                        friendSearchError = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_user_search)
                    )
                }
        }
    }

    /** Clears the search query and results. */
    fun clearFriendSearch() {
        friendSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(friendSearchQuery = "", friendSearchResults = emptyList(), isSearchingFriends = false, friendSearchError = null)
    }

    /**
     * Re-runs the last search silently (no debounce, no spinner) so the statuses (Add / Cancel
     * request) reflect changes made on another screen while this ViewModel stayed alive. No-op
     * without an active query.
     */
    fun refreshFriendSearchResults() {
        val userId = sessionManager.getUserId() ?: return
        val trimmed = _uiState.value.friendSearchQuery.trim()
        if (trimmed.length < MIN_FRIEND_QUERY_LENGTH) return

        // Results whose profile is already in the Room cache (the user just opened it and
        // sent/cancelled a request there, see FriendProfileViewModel.cacheFriendProfile) take the
        // status from the cache IMMEDIATELY. The network call below still runs as the authoritative
        // confirmation (e.g. it catches changes made on ANOTHER device).
        viewModelScope.launch {
            val current = _uiState.value.friendSearchResults
            val merged = current.map { result ->
                userRepository.observeCachedFriendProfile(userId, result.id).first()
                    ?.friendshipStatus?.let { status -> result.copy(status = status) } ?: result
            }
            if (merged != current) {
                _uiState.value = _uiState.value.copy(friendSearchResults = merged)
            }
        }

        friendSearchJob?.cancel()
        friendSearchJob = viewModelScope.launch {
            userRepository.searchUsers(trimmed).onSuccess { results ->
                _uiState.value = _uiState.value.copy(friendSearchResults = results)
            }
        }
    }

    /**
     * Cancels a sent request (PENDING_SENT -> NONE in the search results). The backend has no
     * separate "cancel": the DELETE endpoint used for unfriend/reject removes the row regardless of
     * direction or status.
     */
    fun cancelFriendRequest(targetUserId: String) {
        // While an action for this id is in flight, a new tap is IGNORED (the button is also
        // disabled) instead of cancelling the running call: the call may already have reached the
        // server, and a cancelled client would never sync the UI with the result.
        if (targetUserId in _uiState.value.friendActionInFlightIds) return
        val userId = sessionManager.getUserId() ?: return
        val previousResults = _uiState.value.friendSearchResults
        // The sent list is updated optimistically together with the search results.
        val previousSent = _uiState.value.sentRequests
        _uiState.value = _uiState.value.copy(
            friendSearchResults = previousResults.map { if (it.id == targetUserId) it.copy(status = "NONE") else it },
            sentRequests = previousSent.filterNot { it.id == targetUserId },
            friendActionInFlightIds = _uiState.value.friendActionInFlightIds + targetUserId
        )
        viewModelScope.launch {
            // Also written to the Room cache FriendProfileScreen reads, so the change is visible
            // there and refreshFriendSearchResults' cache merge does not bring back the old state.
            userRepository.updateCachedFriendshipStatus(userId, targetUserId, "NONE")
            userRepository.removeFriend(userId, targetUserId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friendSearchResults = previousResults,
                    sentRequests = previousSent,
                    friendActionError = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_cancel_request)
                )
                userRepository.updateCachedFriendshipStatus(userId, targetUserId, "PENDING_SENT")
            }
            _uiState.value = _uiState.value.copy(friendActionInFlightIds = _uiState.value.friendActionInFlightIds - targetUserId)
        }
    }

    /**
     * Optimistic: the search result changes to PENDING_SENT at once and reverts if the call fails.
     */
    fun sendFriendRequest(targetUserId: String) {
        // See the note in cancelFriendRequest().
        if (targetUserId in _uiState.value.friendActionInFlightIds) return
        val userId = sessionManager.getUserId() ?: return
        val previousResults = _uiState.value.friendSearchResults
        // Added to sentRequests immediately (optimistic, no network), built from the same search
        // result (it has id/username/avatarUrl).
        val previousSent = _uiState.value.sentRequests
        val targetResult = previousResults.find { it.id == targetUserId }
        val newSentEntry = targetResult?.let { PendingRequest(id = it.id, username = it.username, avatarUrl = it.avatarUrl) }
        _uiState.value = _uiState.value.copy(
            friendSearchResults = previousResults.map { if (it.id == targetUserId) it.copy(status = "PENDING_SENT") else it },
            sentRequests = if (newSentEntry != null) previousSent + newSentEntry else previousSent,
            friendActionInFlightIds = _uiState.value.friendActionInFlightIds + targetUserId
        )
        viewModelScope.launch {
            // See the note in cancelFriendRequest().
            userRepository.updateCachedFriendshipStatus(userId, targetUserId, "PENDING_SENT")
            userRepository.addFriend(userId, targetUserId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friendSearchResults = previousResults,
                    sentRequests = previousSent,
                    friendActionError = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_send_request)
                )
                userRepository.updateCachedFriendshipStatus(userId, targetUserId, "NONE")
            }
            _uiState.value = _uiState.value.copy(friendActionInFlightIds = _uiState.value.friendActionInFlightIds - targetUserId)
        }
    }

    /** Optimistically removes the request from the list; accepting refreshes the friend list. */
    fun acceptFriendRequest(requesterId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousPending = _uiState.value.pendingRequests
        _uiState.value = _uiState.value.copy(pendingRequests = previousPending.filterNot { it.id == requesterId })
        pendingActionJobs[requesterId]?.cancel()
        pendingActionJobs[requesterId] = viewModelScope.launch {
            userRepository.acceptFriendRequest(userId, requesterId)
                .onSuccess {
                    // Also written to the friend profile cache, so FriendProfileScreen opened right
                    // after accepting does not briefly show the old PENDING_RECEIVED status.
                    userRepository.updateCachedFriendshipStatus(userId, requesterId, "ACCEPTED")
                    userRepository.getFriends(userId).onSuccess { friends ->
                        _uiState.value = _uiState.value.copy(friends = friends)
                    }
                    // Accepting is also possible from the search results - refresh their status
                    // ("Accept" -> "Already friends").
                    refreshFriendSearchResults()
                    // Refresh the incoming requests cache - the bottom navigation badge reads it.
                    userRepository.getPendingFriendRequests(userId)
                }
                .onFailure { e ->
                    // The item is NOT put back on failure: if the backend refused (e.g. the request
                    // no longer exists), offering it again would only fail again in a loop. The
                    // error is only logged.
                    Log.w("ProfileViewModel", "acceptFriendRequest nije uspeo za requesterId=$requesterId (tiho, ne prikazuje se korisniku): ${e.message}")
                }
        }
    }

    /** Rejects an incoming request (optimistically removed from the list, like accept). */
    fun rejectFriendRequest(requesterId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousPending = _uiState.value.pendingRequests
        _uiState.value = _uiState.value.copy(pendingRequests = previousPending.filterNot { it.id == requesterId })
        pendingActionJobs[requesterId]?.cancel()
        pendingActionJobs[requesterId] = viewModelScope.launch {
            // Same principle as acceptFriendRequest, see the note there.
            userRepository.rejectFriendRequest(userId, requesterId)
                .onSuccess {
                    // See the note in acceptFriendRequest().
                    userRepository.updateCachedFriendshipStatus(userId, requesterId, "NONE")
                    // Refresh the incoming requests cache - the bottom navigation badge reads it.
                    userRepository.getPendingFriendRequests(userId)
                }
                .onFailure { e ->
                    Log.w("ProfileViewModel", "rejectFriendRequest nije uspeo za requesterId=$requesterId (tiho, ne prikazuje se korisniku): ${e.message}")
                }
        }
    }

    /**
     * Removes a friend optimistically (restored on failure); the screen asks for confirmation
     * first.
     */
    fun removeFriend(friendId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousFriends = _uiState.value.friends
        _uiState.value = _uiState.value.copy(friends = previousFriends.filterNot { it.friendId == friendId })
        viewModelScope.launch {
            userRepository.removeFriend(userId, friendId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friends = previousFriends,
                    friendActionError = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_remove_friend)
                )
            }
        }
    }

    /** The screen has shown the friend action error. */
    fun consumeFriendActionError() {
        _uiState.value = _uiState.value.copy(friendActionError = null)
    }

    /**
     * Saves the new username/bio/avatar (PUT /api/users/{userId}/profile). The whole `UserProfile`
     * is sent, so the currently loaded profile is the base with username/bio changed; the avatar
     * changes only if the user picked a new picture (`newAvatarDataUri == null` keeps the current
     * one). The backend derives the other fields itself. Errors such as "Username is already taken"
     * come from the backend via `UserRepositoryImpl.parseErrorMessage` and are shown to the user.
     */
    fun updateProfile(username: String, bio: String?, newAvatarDataUri: String? = null) {
        val userId = sessionManager.getUserId() ?: return
        val current = _uiState.value.userProfile
        val base = current ?: dev.milinko.movielens.data.remote.user.UserProfile(
            userId = userId,
            username = username,
            email = sessionManager.getEmail() ?: ""
        )
        val body = base.copy(
            username = username,
            bio = bio,
            avatar = newAvatarDataUri ?: base.avatar
        )

        // Optimistic save: the new values are shown IMMEDIATELY (the form closes without waiting)
        // and syncing runs in the background. If the server refuses (e.g. the username is taken),
        // the previous state is restored and the form reopens with the user's input and a message
        // (failedProfileDraft), so nothing has to be typed again.
        val previousProfile = current
        val previousUsername = sessionManager.getUsername()
        val previousAvatar = sessionManager.getAvatarUrl()

        _uiState.value = _uiState.value.copy(
            userProfile = body,
            isSavingProfile = true,
            editProfileError = null,
            failedProfileDraft = null
        )
        sessionManager.updateCachedIdentity(body.username, body.avatar)
        refreshSessionUser()

        viewModelScope.launch {
            userRepository.updateUserProfile(userId, body)
                .onSuccess { updated ->
                    _uiState.value = _uiState.value.copy(isSavingProfile = false, userProfile = updated)
                    // The server may normalize values (e.g. the avatar URL after upload), so the
                    // cached identity follows what was actually saved.
                    sessionManager.updateCachedIdentity(updated.username, updated.avatar)
                    refreshSessionUser()
                }
                .onFailure { e ->
                    sessionManager.updateCachedIdentity(previousUsername, previousAvatar)
                    refreshSessionUser()
                    _uiState.value = _uiState.value.copy(
                        isSavingProfile = false,
                        userProfile = previousProfile,
                        editProfileError = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_profile_not_saved),
                        failedProfileDraft = ProfileDraft(username, bio, newAvatarDataUri)
                    )
                }
        }
    }

    /** The screen has shown the profile edit error. */
    fun consumeEditProfileError() {
        _uiState.value = _uiState.value.copy(editProfileError = null)
    }

    /** The user cancelled editing again after a failed save. */
    fun discardFailedProfileDraft() {
        _uiState.value = _uiState.value.copy(failedProfileDraft = null, editProfileError = null)
    }

    private fun refreshRoutesState() {
        _baseUrl.value = urlStorage.getBaseUrl()
        _savedRoutes.value = urlStorage.getSavedRoutes()
    }

    /** Selects [newUrl] as the backend URL (developer settings). */
    fun updateBaseUrl(newUrl: String) {
        viewModelScope.launch {
            try {
                urlStorage.setBaseUrl(newUrl)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Backend URL sačuvan."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage(dev.milinko.movielens.util.friendlyMessage(e, "Greška pri čuvanju URL-a.")))
            }
        }
    }

    /** Adds a backend URL to the saved list. */
    fun addSavedRoute(url: String) {
        viewModelScope.launch {
            try {
                urlStorage.addRoute(url)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Ruta je dodata u listu."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage(dev.milinko.movielens.util.friendlyMessage(e, "Greška pri dodavanju rute.")))
            }
        }
    }

    /** Removes a backend URL from the saved list. */
    fun removeSavedRoute(url: String) {
        viewModelScope.launch {
            try {
                urlStorage.removeRoute(url)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Ruta je obrisana."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage(dev.milinko.movielens.util.friendlyMessage(e, "Greška pri brisanju rute.")))
            }
        }
    }

    /**
     * Loads the signed-in user's profile, friends, requests, activity and taste profile (cache
     * first, then network). Skipped if the same profile is already loaded, so returning to the tab
     * does not hit the network every time; [forceRefresh] (pull-to-refresh, retry) bypasses that.
     */
    fun loadProfile(forceRefresh: Boolean = false) {
        refreshSessionUser()
        // The friend search results do not depend on the guard below, so they are refreshed on
        // every entry (cheap, a no-op without an active query) in case a status changed on another
        // screen (FriendProfileScreen) while this ViewModel stayed alive.
        refreshFriendSearchResults()
        val userId = sessionManager.getUserId() ?: return

        // Avoid reloading the same profile repeatedly.
        if (!forceRefresh && _uiState.value.userProfile?.userId == userId) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !forceRefresh,
                isRefreshingFriends = forceRefresh,
                error = null
            )

            // Show the Room cache first (instant, works offline); the network calls below refresh
            // it and `_uiState` is overwritten with fresher data when it arrives.
            _uiState.value = _uiState.value.copy(
                userProfile = userRepository.observeCachedUserProfile(userId).first() ?: _uiState.value.userProfile,
                friends = userRepository.observeCachedFriends(userId).first().ifEmpty { _uiState.value.friends },
                friendActivity = userRepository.observeCachedFriendActivity(userId).first().ifEmpty { _uiState.value.friendActivity },
                pendingRequests = userRepository.observeCachedPendingRequests(userId).first().ifEmpty { _uiState.value.pendingRequests },
                sentRequests = userRepository.observeCachedSentRequests(userId).first().ifEmpty { _uiState.value.sentRequests },
                tasteProfile = userRepository.observeCachedTasteProfile(userId).first().ifEmpty { _uiState.value.tasteProfile }
            )

            try {
                // All six calls are independent and run in parallel, otherwise the waiting time
                // adds up (6 round trips instead of 1).
                val profileDeferred = async { userRepository.getUserProfile(userId) }
                val friendsDeferred = async { userRepository.getFriends(userId) }
                val activityDeferred = async { userRepository.getFriendActivity(userId) }
                val pendingDeferred = async { userRepository.getPendingFriendRequests(userId) }
                val sentDeferred = async { userRepository.getSentFriendRequests(userId) }
                val tasteProfileDeferred = async { userRepository.getTasteProfile(userId) }

                val profileResult = profileDeferred.await()
                val friendsResult = friendsDeferred.await()
                val activityResult = activityDeferred.await()
                val pendingResult = pendingDeferred.await()
                val sentResult = sentDeferred.await()
                val tasteProfileResult = tasteProfileDeferred.await()

                // Every fresh profile from the network also updates the SessionManager cache, so a
                // stale cached identity (e.g. an old avatar) fixes itself.
                profileResult.getOrNull()?.let { fresh ->
                    sessionManager.updateCachedIdentity(fresh.username, fresh.avatar)
                }

                // Each call returns a Result (no exception), so the catch below never sees network
                // failures - the error is derived from the results. It is shown if neither the
                // profile nor the friends arrived; cached data (if any) stays visible below it.
                val loadError = profileResult.exceptionOrNull()
                    ?.takeIf { friendsResult.isFailure }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshingFriends = false,
                    error = loadError?.let { dev.milinko.movielens.util.friendlyMessage(it, dev.milinko.movielens.R.string.error_profile_unavailable) },
                    userProfile = profileResult.getOrNull() ?: _uiState.value.userProfile,
                    friends = friendsResult.getOrNull() ?: _uiState.value.friends,
                    friendActivity = activityResult.getOrNull() ?: _uiState.value.friendActivity,
                    pendingRequests = pendingResult.getOrNull() ?: _uiState.value.pendingRequests,
                    sentRequests = sentResult.getOrNull() ?: _uiState.value.sentRequests,
                    tasteProfile = tasteProfileResult.getOrNull() ?: _uiState.value.tasteProfile
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshingFriends = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_load_profile)
                )
            }
        }
    }

    /** "Try again" on the error card - bypasses the "already loaded" guard in loadProfile. */
    fun retry() {
        loadProfile(forceRefresh = true)
    }

    /** Pull-to-refresh on the Friends tab - see loadProfile(forceRefresh). */
    fun refreshFriends() {
        loadProfile(forceRefresh = true)
    }

    /** Switches between the dark and light theme. */
    fun toggleDarkMode() {
        themePreferences.toggleDarkMode()
    }
}
