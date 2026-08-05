package dev.milinko.movielens.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val watchlist: List<WatchlistItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val requiresSignIn: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** Učitava biblioteku za trenutno ulogovanog korisnika (iz SessionManager-a). */
    fun loadLibrary() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            _uiState.value = _uiState.value.copy(requiresSignIn = true, isLoading = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, requiresSignIn = false)

            // Load both watchlist and history
            val watchlistResult = userRepository.getWatchlist(userId)
            val historyResult = userRepository.getHistory(userId)

            watchlistResult.onSuccess { watchlist ->
                historyResult.onSuccess { history ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        watchlist = watchlist,
                        history = history
                    )
                }.onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Greška pri učitavanju istorije"
                    )
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Greška pri učitavanju watchlist-a"
                )
            }
        }
    }

    fun removeFromWatchlist(tmdbId: Int) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.removeFromWatchlist(userId, tmdbId)
                .onSuccess {
                    val updated = _uiState.value.watchlist.filter { it.tmdbId != tmdbId }
                    _uiState.value = _uiState.value.copy(watchlist = updated)
                }
        }
    }
}

