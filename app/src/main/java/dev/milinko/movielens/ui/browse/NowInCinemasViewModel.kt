package dev.milinko.movielens.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.BrowsePreferences
import dev.milinko.movielens.data.pref.BrowseViewMode
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
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

/** Now showing / upcoming filter. */
enum class CinemaStatusFilter { ALL, NOW, UPCOMING }

/** Sort order of the In cinemas list. */
enum class CinemaSort { RATING, TITLE, CINEMA_COUNT }

private data class NowInCinemasState(
    val movies: List<NowInCinemasMovie> = emptyList(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val selectedCinemas: Set<String> = emptySet(),
    val status: CinemaStatusFilter = CinemaStatusFilter.ALL,
    val sort: CinemaSort = CinemaSort.RATING,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

/** State of the In cinemas screen: the filtered and sorted list plus the active filters. */
data class NowInCinemasUiState(
    val movies: List<BrowseMovie> = emptyList(),
    val availableGenres: List<Genre> = emptyList(),
    val availableCinemas: List<String> = emptyList(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val selectedCinemas: Set<String> = emptySet(),
    val status: CinemaStatusFilter = CinemaStatusFilter.ALL,
    val sort: CinemaSort = CinemaSort.RATING,
    val viewMode: BrowseViewMode = BrowseViewMode.GRID,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasActiveFilters: Boolean = false,
    val error: String? = null
)

/**
 * "In cinemas" - See all. The whole scraped programme comes in one call (/now-in-cinemas); all
 * filters (title, genre, cinema/chain, Now/Upcoming) and sorting are local, without network calls.
 */
@HiltViewModel
class NowInCinemasViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val browsePreferences: BrowsePreferences
) : ViewModel() {

    private val state = MutableStateFlow(NowInCinemasState())

    val uiState: StateFlow<NowInCinemasUiState> =
        combine(state, browsePreferences.viewMode) { s, viewMode -> s.toUiState(viewMode) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NowInCinemasUiState(isLoading = true))

    init {
        load(isRefresh = false)
    }

    private fun load(isRefresh: Boolean) {
        viewModelScope.launch {
            state.update {
                if (isRefresh) it.copy(isRefreshing = true, error = null) else it.copy(isLoading = true, error = null)
            }
            movieRepository.getNowInCinemas()
                .onSuccess { movies ->
                    state.update {
                        it.copy(movies = movies.distinctBy { m -> m.tmdbId }, isLoading = false, isRefreshing = false)
                    }
                }
                .onFailure { e ->
                    state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = friendlyMessage(e, dev.milinko.movielens.R.string.error_cinemas_unavailable)
                        )
                    }
                }
        }
    }

    /** Pull-to-refresh. */
    fun refresh() = load(isRefresh = true)

    /** Reloads after an error. */
    fun retry() = load(isRefresh = false)

    // Filter and sort events - applied locally to the loaded list.
    fun onQueryChange(query: String) = state.update { it.copy(query = query) }

    fun onToggleGenre(genreId: Int) = state.update {
        val selected = it.selectedGenreIds
        it.copy(selectedGenreIds = if (genreId in selected) selected - genreId else selected + genreId)
    }

    fun onToggleCinema(cinema: String) = state.update {
        val selected = it.selectedCinemas
        it.copy(selectedCinemas = if (cinema in selected) selected - cinema else selected + cinema)
    }

    fun onStatusChange(status: CinemaStatusFilter) = state.update { it.copy(status = status) }

    fun onSortChange(sort: CinemaSort) = state.update { it.copy(sort = sort) }

    fun toggleViewMode() = browsePreferences.toggleViewMode()

    private fun NowInCinemasState.toUiState(viewMode: BrowseViewMode): NowInCinemasUiState {
        val q = query.trim()
        val filtered = movies.filter { movie ->
            val upcoming = movie.isUpcoming == true
            (q.isEmpty() || movie.title.contains(q, ignoreCase = true)) &&
                (selectedGenreIds.isEmpty() || movie.genres.orEmpty().any { it.id in selectedGenreIds }) &&
                (selectedCinemas.isEmpty() || movie.cinemas.any { it in selectedCinemas }) &&
                when (status) {
                    CinemaStatusFilter.ALL -> true
                    CinemaStatusFilter.NOW -> !upcoming
                    CinemaStatusFilter.UPCOMING -> upcoming
                }
        }
        val sorted = when (sort) {
            CinemaSort.RATING -> filtered.sortedByDescending { it.voteAverage }
            CinemaSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            CinemaSort.CINEMA_COUNT -> filtered.sortedByDescending { it.cinemas.size }
        }
        return NowInCinemasUiState(
            movies = sorted.map { movie ->
                BrowseMovie(
                    tmdbId = movie.tmdbId,
                    title = movie.title,
                    posterPath = movie.posterPath,
                    rating = movie.voteAverage,
                    year = movie.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                    runtimeMinutes = movie.runtime,
                    genreNames = movie.genres.orEmpty().map { it.name },
                    overview = movie.overview,
                    subtitle = cinemasSubtitle(movie.cinemas)
                )
            },
            availableGenres = movies.flatMap { it.genres.orEmpty() }.distinctBy { it.id }.sortedBy { it.name },
            availableCinemas = movies.flatMap { it.cinemas }.distinct().sorted(),
            query = query,
            selectedGenreIds = selectedGenreIds,
            selectedCinemas = selectedCinemas,
            status = status,
            sort = sort,
            viewMode = viewMode,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            hasActiveFilters = q.isNotEmpty() || selectedGenreIds.isNotEmpty() ||
                selectedCinemas.isNotEmpty() || status != CinemaStatusFilter.ALL,
            error = error
        )
    }
}
