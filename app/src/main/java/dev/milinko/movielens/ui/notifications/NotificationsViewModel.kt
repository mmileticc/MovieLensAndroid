package dev.milinko.movielens.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.user.NotificationItem
import dev.milinko.movielens.data.repository.UserRepository
import dev.milinko.movielens.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the notification history (and the unread count for the Home bell). */
data class NotificationsUiState(
    val items: List<NotificationItem> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val requiresSignIn: Boolean = false,
    val error: String? = null
)

/**
 * Notification history (everything that also arrived as a push: shared movies, friend requests,
 * likes, watchlist movies in cinemas). The bell on Home uses it too (only
 * [NotificationsUiState.unreadCount]).
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    /** @param markRead true on the Notifications screen: after loading, everything is marked as read. */
    fun load(markRead: Boolean = false) {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _uiState.value = NotificationsUiState(requiresSignIn = true)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = _uiState.value.items.isEmpty(), requiresSignIn = false, error = null)
            userRepository.getNotifications(userId)
                .onSuccess { items ->
                    _uiState.value = _uiState.value.copy(
                        items = items,
                        unreadCount = items.count { !it.isRead },
                        isLoading = false
                    )
                    // Items on the screen stay highlighted as new while the user is there; only the
                    // counter is reset.
                    if (markRead && items.any { !it.isRead }) {
                        userRepository.markNotificationsRead(userId).onSuccess {
                            _uiState.value = _uiState.value.copy(unreadCount = 0)
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyMessage(e, R.string.notifications_load_failed)
                    )
                }
        }
    }
}
