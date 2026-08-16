package dev.milinko.movielens.ui.movienight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieNightUiState(
    val suggestions: List<MovieSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    // Odvojeno od isLoading - "Osveži" spinner ide na dugme, ne preko cele liste (korisnik i dalje
    // vidi stare predloge dok se novi ne učitaju, umesto da ekran trepne prazan).
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieNightViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val friendId: String = checkNotNull(savedStateHandle["friendId"])

    private val _uiState = MutableStateFlow(MovieNightUiState())
    val uiState: StateFlow<MovieNightUiState> = _uiState.asStateFlow()

    // Prati koja je TMDB discover strana trenutno prikazana - "Osveži" rotira kroz 1..MAX_PAGE
    // pa se vrati na početak, umesto da raste unedogled (posle par strana bi discover mogao
    // da vrati prazan rezultat za uzak par žanrova).
    private var currentPage = 1

    private companion object {
        const val MAX_PAGE = 5
    }

    fun loadSuggestions() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            userRepository.getMovieNightSuggestions(userId, friendId, page = currentPage)
                .onSuccess { suggestions ->
                    _uiState.value = _uiState.value.copy(isLoading = false, suggestions = suggestions)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri učitavanju predloga")
                    )
                }
        }
    }

    /** "Osveži predloge" - traži sledeću TMDB discover stranu (rotira 1..MAX_PAGE). */
    fun refreshSuggestions() {
        val userId = sessionManager.getUserId() ?: return
        val nextPage = if (currentPage >= MAX_PAGE) 1 else currentPage + 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            userRepository.getMovieNightSuggestions(userId, friendId, page = nextPage)
                .onSuccess { suggestions ->
                    currentPage = nextPage
                    _uiState.value = _uiState.value.copy(isRefreshing = false, suggestions = suggestions)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri osvežavanju predloga")
                    )
                }
        }
    }
}
