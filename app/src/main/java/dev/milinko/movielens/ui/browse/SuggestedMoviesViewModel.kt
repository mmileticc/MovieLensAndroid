package dev.milinko.movielens.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.BrowsePreferences
import dev.milinko.movielens.data.pref.BrowseViewMode
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Raw state (all loaded pages + filters); the UI gets the already filtered list, see uiState. */
private data class SuggestedState(
    val movies: List<MovieSearchResult> = emptyList(),
    val genreNames: Map<Int, String> = emptyMap(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null
)

/** State of the Suggested screen: the filtered list, paging flags and the active filters. */
data class SuggestedMoviesUiState(
    val movies: List<BrowseMovie> = emptyList(),
    // Only genres present among the loaded movies (an empty filter makes no sense).
    val availableGenres: List<Genre> = emptyList(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val viewMode: BrowseViewMode = BrowseViewMode.GRID,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasActiveFilters: Boolean = false,
    val error: String? = null
)

/**
 * "Suggested for you" - See all. The same list as the Home row (/featured, personalized by genre for
 * a signed-in user), with infinite scrolling and local filters (title, genre) over all loaded pages.
 * Pages are deduplicated by tmdbId like HomeViewModel.loadMoreTrending.
 */
@HiltViewModel
class SuggestedMoviesViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val sessionManager: SessionManager,
    private val browsePreferences: BrowsePreferences
) : ViewModel() {

    private val state = MutableStateFlow(SuggestedState())
    private var currentPage = 1

    // When a filter leaves too few results, a few more pages are loaded automatically (so a genre
    // filter does not look empty just because the first page lacks that genre). Reset on every
    // filter change.
    private var autoLoadsRemaining = MAX_AUTO_LOADS

    private companion object {
        const val MIN_FILTERED_RESULTS = 10
        const val MAX_AUTO_LOADS = 3
    }

    val uiState: StateFlow<SuggestedMoviesUiState> =
        combine(state, browsePreferences.viewMode) { s, viewMode -> s.toUiState(viewMode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SuggestedMoviesUiState(isLoading = true))

    init {
        loadGenres()
        loadFirstPage(isRefresh = false)
    }

    private fun loadGenres() {
        viewModelScope.launch {
            movieRepository.getGenres().onSuccess { genres ->
                state.update { it.copy(genreNames = genres.associate { g -> g.id to g.name }) }
            }
        }
    }

    private fun loadFirstPage(isRefresh: Boolean) {
        viewModelScope.launch {
            state.update {
                if (isRefresh) it.copy(isRefreshing = true, error = null) else it.copy(isLoading = true, error = null)
            }
            movieRepository.getFeaturedMovies(sessionManager.getUserId(), page = 1)
                .onSuccess { movies ->
                    currentPage = 1
                    state.update {
                        it.copy(
                            movies = movies.distinctBy { m -> m.tmdbId },
                            canLoadMore = movies.isNotEmpty(),
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                    maybeAutoLoadMore()
                }
                .onFailure { e ->
                    state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = friendlyMessage(e, dev.milinko.movielens.R.string.error_suggestions_unavailable)
                        )
                    }
                }
        }
    }

    /** Pull-to-refresh: reloads from the first page. */
    fun refresh() {
        autoLoadsRemaining = MAX_AUTO_LOADS
        loadFirstPage(isRefresh = true)
    }

    /** Reloads after an error. */
    fun retry() = loadFirstPage(isRefresh = false)

    /** Loads the next page (infinite scroll). */
    fun loadMore() {
        val s = state.value
        if (s.isLoading || s.isLoadingMore || s.isRefreshing || !s.canLoadMore) return

        val nextPage = currentPage + 1
        viewModelScope.launch {
            state.update { it.copy(isLoadingMore = true) }
            movieRepository.getFeaturedMovies(sessionManager.getUserId(), page = nextPage)
                .onSuccess { movies ->
                    currentPage = nextPage
                    state.update {
                        val existingIds = it.movies.map { m -> m.tmdbId }.toSet()
                        it.copy(
                            movies = it.movies + movies.filter { m -> m.tmdbId !in existingIds },
                            canLoadMore = movies.isNotEmpty(),
                            isLoadingMore = false
                        )
                    }
                    maybeAutoLoadMore()
                }
                .onFailure {
                    state.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    // Filter events - applied locally to all loaded pages.
    fun onQueryChange(query: String) {
        autoLoadsRemaining = MAX_AUTO_LOADS
        state.update { it.copy(query = query) }
        maybeAutoLoadMore()
    }

    fun onToggleGenre(genreId: Int) {
        autoLoadsRemaining = MAX_AUTO_LOADS
        state.update {
            val selected = it.selectedGenreIds
            it.copy(selectedGenreIds = if (genreId in selected) selected - genreId else selected + genreId)
        }
        maybeAutoLoadMore()
    }

    fun toggleViewMode() = browsePreferences.toggleViewMode()

    private fun maybeAutoLoadMore() {
        val s = state.value
        if (!s.hasActiveFilters() || autoLoadsRemaining <= 0 || !s.canLoadMore) return
        if (s.filteredMovies().size >= MIN_FILTERED_RESULTS) return
        autoLoadsRemaining--
        loadMore()
    }

    private fun SuggestedState.hasActiveFilters() = query.isNotBlank() || selectedGenreIds.isNotEmpty()

    private fun SuggestedState.filteredMovies(): List<MovieSearchResult> {
        val q = query.trim()
        return movies.filter { movie ->
            (q.isEmpty() || movie.title.contains(q, ignoreCase = true)) &&
                (selectedGenreIds.isEmpty() || movie.genreIds.orEmpty().any { it in selectedGenreIds })
        }
    }

    private fun SuggestedState.toUiState(viewMode: BrowseViewMode): SuggestedMoviesUiState {
        val presentGenreIds = movies.flatMap { it.genreIds.orEmpty() }.toSet()
        val availableGenres = presentGenreIds
            .mapNotNull { id -> genreNames[id]?.let { Genre(id, it) } }
            .sortedBy { it.name }
        return SuggestedMoviesUiState(
            movies = filteredMovies().map { movie ->
                BrowseMovie(
                    tmdbId = movie.tmdbId,
                    title = movie.title,
                    posterPath = movie.posterPath,
                    rating = movie.voteAverage,
                    year = movie.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                    runtimeMinutes = null,
                    genreNames = movie.genreIds.orEmpty().mapNotNull { genreNames[it] },
                    overview = movie.overview,
                    subtitle = null
                )
            },
            availableGenres = availableGenres,
            query = query,
            selectedGenreIds = selectedGenreIds,
            viewMode = viewMode,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            isRefreshing = isRefreshing,
            hasActiveFilters = hasActiveFilters(),
            error = error
        )
    }
}
