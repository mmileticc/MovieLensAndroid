package dev.milinko.movielens.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.async
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

    // Da li je biblioteka već bar jednom učitana za trenutno ulogovanog korisnika - koristi se
    // SAMO da odlučimo da li da prikažemo full-page spinner (prvi put) ili da refresh bude tih
    // (svaki sledeći ulazak na Library tab). Mreža se i dalje uvek zove - inače lista ne bi
    // pokazala promene napravljene na drugim ekranima (npr. dodavanje u watchlistu sa Movie
    // Detail ekrana) dok se app ne restartuje.
    private var loadedForUserId: String? = null

    /** Učitava biblioteku za trenutno ulogovanog korisnika (iz SessionManager-a). */
    fun loadLibrary() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            loadedForUserId = null
            _uiState.value = _uiState.value.copy(requiresSignIn = true, isLoading = false)
            return
        }

        val isInitialLoad = loadedForUserId != userId

        viewModelScope.launch {
            if (isInitialLoad) {
                _uiState.value = _uiState.value.copy(isLoading = true, requiresSignIn = false)
            }

            // Watchlist i istorija su nezavisni pozivi - paralelno umesto sekvencijalno
            // (prepolovljuje vreme čekanja na spor/udaljen backend).
            val watchlistDeferred = async { userRepository.getWatchlist(userId) }
            val historyDeferred = async { userRepository.getHistory(userId) }
            val watchlistResult = watchlistDeferred.await()
            val historyResult = historyDeferred.await()

            watchlistResult.onSuccess { watchlist ->
                historyResult.onSuccess { history ->
                    loadedForUserId = userId
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

    /** Optimistički uklanja iz watchlist-e odmah (bez čekanja mreže), vraća stanje ako poziv ne uspe. */
    fun removeFromWatchlist(tmdbId: Int) {
        val userId = sessionManager.getUserId() ?: return
        val previousWatchlist = _uiState.value.watchlist
        _uiState.value = _uiState.value.copy(watchlist = previousWatchlist.filter { it.tmdbId != tmdbId })

        viewModelScope.launch {
            userRepository.removeFromWatchlist(userId, tmdbId)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        watchlist = previousWatchlist,
                        error = e.message ?: "Greška pri uklanjanju iz watchlist-e"
                    )
                }
        }
    }
}

