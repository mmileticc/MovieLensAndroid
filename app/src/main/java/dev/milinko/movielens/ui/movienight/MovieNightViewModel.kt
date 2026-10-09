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
    // Local filter by one of the shared genres (null = all).
    val selectedGenreId: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null
)

/** State of the Movie Night screen: the filtered suggestions plus the shared header data. */
data class MovieNightUiState(
    val suggestions: List<MovieSuggestion> = emptyList(),
    val totalLoaded: Int = 0,
    val friendUsername: String? = null,
    val sharedGenres: List<Genre> = emptyList(),
    val matchScore: Float? = null,
    // "You / Friend" affinity for the main shared genre - the same for every suggestion, so it is
    // shown once in the header.
    val yourMatch: Float? = null,
    val theirMatch: Float? = null,
    val selectedGenreId: Int? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

/**
 * Movie Night suggestions for the user and a friend. Pages are APPENDED (infinite scroll), so a
 * suggestion the user liked is never lost; pull-to-refresh starts over with fresh data. The first
 * page is shown immediately from the cache (prefetched on the friend's profile) and then silently
 * refreshed.
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

    /**
     * First load (and "Try again"): the cache for an instant display, then a silent fresh first
     * page.
     */
    fun loadSuggestions() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            raw.update { it.copy(isLoading = it.suggestions.isEmpty(), error = null) }
            userRepository.getMovieNightSuggestions(userId, friendId, page = 1)
                .onSuccess { result -> applyFirstPage(result) }
                .onFailure { e -> showError(e) }

            // The cache (memory/Room) may be stale, e.g. from before the backend started sending
            // the friend's name and shared genres. Silent refresh, no spinner.
            userRepository.getMovieNightSuggestions(userId, friendId, page = 1, forceRefresh = true)
                .onSuccess { result -> if (currentPage == 1) applyFirstPage(result) }
        }
    }

    /** Pull-to-refresh - starts over with fresh data from the server. */
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

    /** Infinite scroll - the next TMDB page is appended (deduplicated by tmdbId). */
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
                    // Silent - the user still has all loaded suggestions; the next scroll tries
                    // again.
                    raw.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    /** Selecting the same genre again turns the filter off. */
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
                // Full-page error only when there is nothing to show.
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
