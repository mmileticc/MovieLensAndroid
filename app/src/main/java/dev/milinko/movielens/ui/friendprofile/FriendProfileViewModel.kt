package dev.milinko.movielens.ui.friendprofile

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.user.FriendProfileResponse
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the friend profile screen. */
data class FriendProfileUiState(
    val profile: FriendProfileResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // The "Movie Night" button makes sense only on someone else's profile.
    val isSelf: Boolean = false,
    // While a friend request is being sent/cancelled from this screen the button is disabled
    // (prevents a double tap while the request is in flight).
    val isRequestActionInFlight: Boolean = false
)

/**
 * Another user's profile and the friendship actions available on it. The profile is shown from the
 * Room cache first and refreshed from the network; friendship changes are written to the cache too.
 */
@HiltViewModel
class FriendProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val friendId: String = checkNotNull(savedStateHandle["friendId"])

    private val _uiState = MutableStateFlow(
        FriendProfileUiState(isSelf = sessionManager.getUserId() == friendId)
    )
    val uiState: StateFlow<FriendProfileUiState> = _uiState.asStateFlow()

    private var requestActionJob: Job? = null

    /** Loads the profile (cache first, then network) and prefetches Movie Night suggestions. */
    fun loadProfile() {
        val myUserId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Instant display from the Room cache (works offline) while the network refreshes in
            // the background, same pattern as ProfileViewModel/MovieDetailViewModel.
            userRepository.observeCachedFriendProfile(myUserId, friendId).first()?.let { cached ->
                _uiState.value = _uiState.value.copy(profile = cached)
            }

            userRepository.getFriendProfile(myUserId, friendId)
                .onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(isLoading = false, profile = profile)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_load_profile)
                    )
                }
        }

        // Prefetch Movie Night suggestions in the background while the user looks at the profile,
        // so MovieNightViewModel can read them from the cache instead of waiting on the button tap
        // (see UserRepository.prefetchMovieNightSuggestions). Completely silent - no loading state,
        // nothing shown if it fails.
        if (myUserId != friendId) {
            viewModelScope.launch {
                userRepository.prefetchMovieNightSuggestions(myUserId, friendId)
            }
        }
    }

    /**
     * Sends a friend request from this screen (the profile can be opened before becoming friends).
     * Optimistically updates `friendshipStatus`, like ProfileViewModel.sendFriendRequest.
     */
    fun sendFriendRequest() {
        // While an action is in flight, a new tap is IGNORED (the button is also disabled via
        // isRequestActionInFlight) instead of cancelling the running call: a Retrofit call may
        // already have reached the server, and a cancelled client would never get the result to
        // sync the UI/cache, leaving client and server out of sync after a few fast taps.
        if (_uiState.value.isRequestActionInFlight) return
        val myUserId = sessionManager.getUserId() ?: return
        val previousProfile = _uiState.value.profile ?: return
        val updatedProfile = previousProfile.copy(friendshipStatus = "PENDING_SENT")
        _uiState.value = _uiState.value.copy(profile = updatedProfile, isRequestActionInFlight = true)
        requestActionJob = viewModelScope.launch {
            // Also written to the Room cache, not only _uiState, so reopening this profile (a new
            // ViewModel) immediately shows "Cancel request" instead of a stale cache entry (see
            // cacheFriendProfile in UserRepository.kt).
            userRepository.cacheFriendProfile(myUserId, friendId, updatedProfile)
            userRepository.addFriend(myUserId, friendId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isRequestActionInFlight = false)
                }
                .onFailure { e ->
                    Log.w("FriendProfileViewModel", "sendFriendRequest nije uspeo: ${e.message}")
                    _uiState.value = _uiState.value.copy(profile = previousProfile, isRequestActionInFlight = false)
                    userRepository.cacheFriendProfile(myUserId, friendId, previousProfile)
                }
        }
    }

    /** Cancels a sent request - the same DELETE endpoint as unfriend/reject (see ProfileViewModel). */
    fun cancelFriendRequest() {
        // Ignore taps while an action is in flight (see sendFriendRequest()).
        if (_uiState.value.isRequestActionInFlight) return
        val myUserId = sessionManager.getUserId() ?: return
        val previousProfile = _uiState.value.profile ?: return
        val updatedProfile = previousProfile.copy(friendshipStatus = "NONE")
        _uiState.value = _uiState.value.copy(profile = updatedProfile, isRequestActionInFlight = true)
        requestActionJob = viewModelScope.launch {
            userRepository.cacheFriendProfile(myUserId, friendId, updatedProfile)
            userRepository.removeFriend(myUserId, friendId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isRequestActionInFlight = false)
                }
                .onFailure { e ->
                    Log.w("FriendProfileViewModel", "cancelFriendRequest nije uspeo: ${e.message}")
                    _uiState.value = _uiState.value.copy(profile = previousProfile, isRequestActionInFlight = false)
                    userRepository.cacheFriendProfile(myUserId, friendId, previousProfile)
                }
        }
    }

    /**
     * Accepts an incoming request from this screen, with the same network call and cache
     * write-through (updateCachedFriendshipStatus) as ProfileViewModel.
     */
    fun acceptFriendRequest() {
        if (_uiState.value.isRequestActionInFlight) return
        val myUserId = sessionManager.getUserId() ?: return
        _uiState.value = _uiState.value.copy(isRequestActionInFlight = true)
        viewModelScope.launch {
            userRepository.acceptFriendRequest(myUserId, friendId)
                .onSuccess {
                    userRepository.updateCachedFriendshipStatus(myUserId, friendId, "ACCEPTED")
                    _uiState.value = _uiState.value.copy(isRequestActionInFlight = false)
                    // Reload from the network - only now does the backend return the full (unlocked)
                    // list of watched movies.
                    loadProfile()
                }
                .onFailure { e ->
                    Log.w("FriendProfileViewModel", "acceptFriendRequest nije uspeo: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isRequestActionInFlight = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_accept_request)
                    )
                }
        }
    }

    /** Rejects an incoming request; `onRejected` is called only after success (to navigate back). */
    fun rejectFriendRequest(onRejected: () -> Unit) {
        if (_uiState.value.isRequestActionInFlight) return
        val myUserId = sessionManager.getUserId() ?: return
        _uiState.value = _uiState.value.copy(isRequestActionInFlight = true)
        viewModelScope.launch {
            userRepository.rejectFriendRequest(myUserId, friendId)
                .onSuccess {
                    userRepository.updateCachedFriendshipStatus(myUserId, friendId, "NONE")
                    _uiState.value = _uiState.value.copy(isRequestActionInFlight = false)
                    onRejected()
                }
                .onFailure { e ->
                    Log.w("FriendProfileViewModel", "rejectFriendRequest nije uspeo: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        isRequestActionInFlight = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_reject_request)
                    )
                }
        }
    }
}
