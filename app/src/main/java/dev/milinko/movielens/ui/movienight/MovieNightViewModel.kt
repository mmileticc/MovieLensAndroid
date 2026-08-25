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

    // Prati koja je TMDB discover strana trenutno prikazana.
    private var currentPage = 1

    // Bugfix (25. avgust 2026, korisnička žalba "za neke prijatelje se predlozi ponavljaju posle
    // 3-4 ekrana, za druge nikad") - RANIJE je "Osveži" rotirao kroz IZMIŠLJEN fiksan broj strana
    // (1..5) bez obzira na to koliko TMDB stvarno ima za dati par žanrova. Širok presek žanrova
    // (npr. Action|Comedy) ima stotine stvarnih stranica - korisnik bi "ispao" iz fiksnog kruga od
    // 5 mnogo pre nego što je TMDB zaista ostao bez filmova, i video iste predloge iznova. Uzak
    // presek (npr. Documentary|War) možda ima manje od 5 stranica - fiksni broj bi tu čak i
    // preterao. Sad prati STVARAN `hasMore` flag iz backend odgovora (koji zna pravi total_pages,
    // videti MovieNightResponse/FriendsRouting.kt) - stranica raste dok god ima još, vraća se na
    // početak tek kad TMDB zaista nema šta novo da ponudi.
    private var hasMore = true

    fun loadSuggestions() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            userRepository.getMovieNightSuggestions(userId, friendId, page = currentPage)
                .onSuccess { result ->
                    hasMore = result.hasMore
                    _uiState.value = _uiState.value.copy(isLoading = false, suggestions = result.suggestions)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri učitavanju predloga")
                    )
                }
        }
    }

    /** "Osveži predloge" - traži sledeću TMDB discover stranu dok god ih ima (`hasMore`), inače
     * se vraća na stranicu 1 (stvaran kraj raspoloživih predloga za ovaj par žanrova, ne izmišljen). */
    fun refreshSuggestions() {
        val userId = sessionManager.getUserId() ?: return
        val nextPage = if (hasMore) currentPage + 1 else 1

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            userRepository.getMovieNightSuggestions(userId, friendId, page = nextPage)
                .onSuccess { result ->
                    currentPage = nextPage
                    hasMore = result.hasMore
                    _uiState.value = _uiState.value.copy(isRefreshing = false, suggestions = result.suggestions)
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
