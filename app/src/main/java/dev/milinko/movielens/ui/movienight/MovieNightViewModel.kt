package dev.milinko.movielens.ui.movienight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.user.MovieNightResponse
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class MovieNightRawState(
    val suggestions: List<MovieSuggestion> = emptyList(),
    val friendUsername: String? = null,
    val sharedGenres: List<Genre> = emptyList(),
    val matchScore: Float? = null,
    // Lokalni filter po jednom od zajedničkih žanrova (null = svi).
    val selectedGenreId: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

data class MovieNightUiState(
    val suggestions: List<MovieSuggestion> = emptyList(),
    val totalLoaded: Int = 0,
    val friendUsername: String? = null,
    val sharedGenres: List<Genre> = emptyList(),
    val matchScore: Float? = null,
    // "Ti / Prijatelj" afinitet za glavni zajednički žanr - isti za sve predloge, pa se prikazuje
    // jednom u zaglavlju (ranije ponovljen kao identične trake u svakom redu).
    val yourMatch: Float? = null,
    val theirMatch: Float? = null,
    val selectedGenreId: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

/**
 * Predlozi za zajedničko gledanje sa prijateljem.
 *
 * UX dorada (oktobar 2026): strane se DODAJU na listu (beskonačan skrol) umesto da "Osveži"
 * zameni sve - ranije se predlog koji se korisniku dopao gubio bez povratka, a posle poslednje
 * strane lista se tiho vraćala na početak. Pull-to-refresh počinje ispočetka sa svežim podacima.
 * Prva strana se prikazuje odmah iz keša (prefetch sa profila prijatelja), pa se tiho osveži.
 */
@HiltViewModel
class MovieNightViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val friendId: String = checkNotNull(savedStateHandle["friendId"])

    private val raw = MutableStateFlow(MovieNightRawState())

    val uiState: StateFlow<MovieNightUiState> = raw
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MovieNightUiState(isLoading = true))

    private var currentPage = 1

    init {
        loadSuggestions()
    }

    /** Prvo učitavanje (i "Pokušaj ponovo"): keš za trenutni prikaz, pa tiho sveža prva strana. */
    fun loadSuggestions() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            raw.update { it.copy(isLoading = it.suggestions.isEmpty(), error = null) }
            userRepository.getMovieNightSuggestions(userId, friendId, page = 1)
                .onSuccess { result -> applyFirstPage(result) }
                .onFailure { e -> showError(e) }

            // Keš (memorija/Room) može biti star - npr. od pre nego što je backend počeo da šalje
            // ime prijatelja i zajedničke žanrove. Tiho osvežavanje, bez spinnera.
            userRepository.getMovieNightSuggestions(userId, friendId, page = 1, forceRefresh = true)
                .onSuccess { result -> if (currentPage == 1) applyFirstPage(result) }
        }
    }

    /** Pull-to-refresh - ispočetka, sa svežim podacima sa servera. */
    fun refresh() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            raw.update { it.copy(isRefreshing = true, error = null) }
            userRepository.getMovieNightSuggestions(userId, friendId, page = 1, forceRefresh = true)
                .onSuccess { result -> applyFirstPage(result) }
                .onFailure { e -> showError(e) }
            raw.update { it.copy(isRefreshing = false) }
        }
    }

    /** Beskonačan skrol - sledeća TMDB strana se dodaje na kraj (dedupe po tmdbId). */
    fun loadMore() {
        val userId = sessionManager.getUserId() ?: return
        val state = raw.value
        if (state.isLoading || state.isLoadingMore || state.isRefreshing || !state.hasMore) return

        val nextPage = currentPage + 1
        viewModelScope.launch {
            raw.update { it.copy(isLoadingMore = true) }
            userRepository.getMovieNightSuggestions(userId, friendId, page = nextPage)
                .onSuccess { result ->
                    currentPage = nextPage
                    raw.update { current ->
                        val existing = current.suggestions.map { it.tmdbId }.toSet()
                        current.copy(
                            suggestions = current.suggestions + result.suggestions.filter { it.tmdbId !in existing },
                            hasMore = result.hasMore,
                            isLoadingMore = false
                        )
                    }
                }
                .onFailure {
                    // Tiho - korisnik i dalje ima sve već učitane predloge; sledeći skrol pokušava ponovo.
                    raw.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    /** Ponovni izbor istog žanra isključuje filter. */
    fun onGenreSelected(genreId: Int) = raw.update {
        it.copy(selectedGenreId = if (it.selectedGenreId == genreId) null else genreId)
    }

    private fun applyFirstPage(result: MovieNightResponse) {
        currentPage = 1
        raw.update {
            it.copy(
                suggestions = result.suggestions.distinctBy { s -> s.tmdbId },
                friendUsername = result.friendUsername ?: it.friendUsername,
                sharedGenres = result.sharedGenres ?: it.sharedGenres,
                matchScore = result.matchScore ?: it.matchScore,
                hasMore = result.hasMore,
                isLoading = false,
                error = null
            )
        }
    }

    private fun showError(e: Throwable) {
        raw.update {
            it.copy(
                isLoading = false,
                // Greška preko cele stranice samo kad nema šta da se prikaže.
                error = if (it.suggestions.isEmpty()) {
                    dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_suggestions_unavailable)
                } else null
            )
        }
    }

    private fun MovieNightRawState.toUiState(): MovieNightUiState {
        val filtered = if (selectedGenreId == null) suggestions else suggestions.filter { selectedGenreId in it.genreIds.orEmpty() }
        val firstScores = suggestions.firstOrNull()?.scores
        return MovieNightUiState(
            suggestions = filtered,
            totalLoaded = suggestions.size,
            friendUsername = friendUsername,
            sharedGenres = sharedGenres,
            matchScore = matchScore,
            yourMatch = firstScores?.yourMatch,
            theirMatch = firstScores?.theirMatch,
            selectedGenreId = selectedGenreId,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            isLoadingMore = isLoadingMore,
            error = error
        )
    }
}
