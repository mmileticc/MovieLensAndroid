package dev.milinko.movielens.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Number of incoming friend requests for the badge on the Friends tab of the bottom navigation.
 *
 * Reads the same Room cache as the Profile/Friends screens (`observeCachedPendingRequests`), so the
 * badge updates as soon as any of them refreshes the cache. Network refresh is limited to once per
 * [REFRESH_INTERVAL_MS] for the whole process, since every screen with the bottom bar has its own
 * instance.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PendingRequestsBadgeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // The user can sign in/out while this ViewModel is alive, so the id is read again in refresh()
    // and the count follows the current user.
    private val userId = MutableStateFlow(sessionManager.getUserId())

    val pendingCount: StateFlow<Int> = userId
        .flatMapLatest { id ->
            if (id == null) flowOf(0) else userRepository.observeCachedPendingRequests(id).map { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Called every time the bottom navigation is shown (entering a screen). */
    fun refresh() {
        val id = sessionManager.getUserId()
        userId.value = id
        if (id == null) return
        val now = System.currentTimeMillis()
        if (now - lastNetworkRefreshAt < REFRESH_INTERVAL_MS) return
        lastNetworkRefreshAt = now
        viewModelScope.launch {
            // Writes the fresh list into the same cache, so pendingCount updates itself. Errors do
            // not matter for the badge.
            userRepository.getPendingFriendRequests(id)
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L

        @Volatile
        var lastNetworkRefreshAt = 0L
    }
}
