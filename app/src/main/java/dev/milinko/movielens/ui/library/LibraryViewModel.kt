package dev.milinko.movielens.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val error: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun loadLibrary(userId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
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

    fun removeFromWatchlist(userId: String, tmdbId: Int) {
        viewModelScope.launch {
            userRepository.removeFromWatchlist(userId, tmdbId)
                .onSuccess {
                    val updated = _uiState.value.watchlist.filter { it.tmdbId != tmdbId }
                    _uiState.value = _uiState.value.copy(watchlist = updated)
                }
        }
    }
}

