package dev.milinko.movielens.ui.library

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sort order of the "To watch" tab. */
enum class WatchlistSort { ADDED, TMDB_RATING, TITLE, YEAR }

/** Sort order of the "Watched" tab. */
enum class HistorySort { WATCHED, MY_RATING, TMDB_RATING, TITLE }

/** Raw state: the lists from the Room cache + filters. The UI gets the filtered/sorted version. */
private data class LibraryRawState(
    val watchlist: List<WatchlistItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    // Watchlist items removed but waiting for the "Undo" snackbar to expire - hidden from the list;
    // the network call is sent only if the user does not undo (see requestRemove).
    val pendingRemovalIds: Set<Int> = emptySet(),
    // The same for the "Watched" tab (removal also deletes the rating - same row on the backend).
    val pendingHistoryRemovalIds: Set<Int> = emptySet(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val watchlistSort: WatchlistSort = WatchlistSort.ADDED,
    val historySort: HistorySort = HistorySort.WATCHED,
    // Minimum own rating on the "Watched" tab (e.g. 8 = only movies I rated 8+); null = all.
    val minMyRating: Int? = null,
    val isLoading: Boolean = false,
    val requiresSignIn: Boolean = false,
    val error: String? = null
)

/** State of the Library screen: the filtered and sorted lists plus the active filters. */
data class LibraryUiState(
    val watchlist: List<WatchlistItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    // Total before filtering - distinguishes "the list is empty" from "the filter matches nothing".
    val watchlistTotal: Int = 0,
    val historyTotal: Int = 0,
    val availableGenres: List<Genre> = emptyList(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val watchlistSort: WatchlistSort = WatchlistSort.ADDED,
    val historySort: HistorySort = HistorySort.WATCHED,
    val minMyRating: Int? = null,
    // Only while NEITHER the local cache NOR the network has returned anything (first visit, empty
    // database).
    val isLoading: Boolean = false,
    val requiresSignIn: Boolean = false,
    val error: String? = null
) {
    val hasActiveFilters: Boolean get() = query.isNotBlank() || selectedGenreIds.isNotEmpty() || minMyRating != null
}

/** One-off events for the screen (snackbars). */
sealed class LibraryEvent {
    /** Show the "Removed · Undo" snackbar for this item. */
    data class WatchlistItemRemoved(val tmdbId: Int, val title: String) : LibraryEvent()
    /** Show the "Removed from watched · Undo" snackbar. */
    data class HistoryItemRemoved(val tmdbId: Int, val title: String) : LibraryEvent()
    /** A short message (e.g. rating saved or failed). */
    data class Message(@StringRes val messageRes: Int) : LibraryEvent()
}

/**
 * The user's watchlist and watched history. Offline-first: Room (UserRepository.observeWatchlist /
 * observeHistory) is the source of truth for the UI - instant and offline. The network
 * (getWatchlist/getHistory) only refreshes the Room cache in the background, and the Flow pushes
 * the new lists into the state.
 *
 * Filters (title, genre, minimum own rating) and sorting are local.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val raw = MutableStateFlow(LibraryRawState())

    val uiState: StateFlow<LibraryUiState> = raw
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private var observingForUserId: String? = null
    private var watchlistCollectJob: Job? = null
    private var historyCollectJob: Job? = null

    // Same pattern as MovieDetailViewModel (historyJob/watchlistJob), but keyed by tmdbId: several
    // movies in the list can change independently, and a tap must never cancel the request for
    // ANOTHER movie. A new action on the SAME movie cancels the previous one if still in flight.
    private val historyJobs = mutableMapOf<Int, Job>()
    private val removalJobs = mutableMapOf<Int, Job>()

    /** Loads the library of the currently signed-in user (from SessionManager). */
    fun loadLibrary() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            stopObservingLocalCache()
            observingForUserId = null
            raw.value = LibraryRawState(requiresSignIn = true)
            return
        }

        if (observingForUserId != userId) {
            observingForUserId = userId
            raw.value = LibraryRawState(requiresSignIn = false)
            startObservingLocalCache(userId)
        }

        refreshFromNetwork(userId)
    }

    /** "Try again" on the error card. */
    fun retry() = loadLibrary()

    private fun startObservingLocalCache(userId: String) {
        stopObservingLocalCache()
        watchlistCollectJob = viewModelScope.launch {
            userRepository.observeWatchlist(userId).collect { watchlist ->
                raw.update { it.copy(watchlist = watchlist) }
            }
        }
        historyCollectJob = viewModelScope.launch {
            userRepository.observeHistory(userId).collect { history ->
                raw.update { it.copy(history = history) }
            }
        }
    }

    private fun stopObservingLocalCache() {
        watchlistCollectJob?.cancel()
        historyCollectJob?.cancel()
    }

    private fun refreshFromNetwork(userId: String) {
        // A spinner only if the screen has nothing at all to show while waiting for the network
        // (e.g. first visit, empty Room); otherwise the refresh is silent over the cached list.
        val hasNothingToShow = raw.value.watchlist.isEmpty() && raw.value.history.isEmpty()

        viewModelScope.launch {
            raw.update { it.copy(isLoading = hasNothingToShow, error = null) }

            // Watchlist and history are independent calls, run in parallel. The results are not
            // written to the state here - the Room Flow collector above does that as soon as
            // getWatchlist/getHistory write the fresh cache. Here only the spinner and the error.
            val watchlistDeferred = async { userRepository.getWatchlist(userId) }
            val historyDeferred = async { userRepository.getHistory(userId) }
            val watchlistResult = watchlistDeferred.await()
            val historyResult = historyDeferred.await()

            val firstException = watchlistResult.exceptionOrNull() ?: historyResult.exceptionOrNull()
            raw.update {
                it.copy(
                    isLoading = false,
                    // The error is ALWAYS shown when a call fails. With a cache the list stays
                    // visible
                    // below the message, so the user knows the data may be stale and can retry.
                    error = firstException?.let { e ->
                        dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_library_refresh)
                    }
                )
            }
        }
    }

    // --- Filters and sorting (local, no network) ---

    fun onQueryChange(query: String) = raw.update { it.copy(query = query) }

    fun onToggleGenre(genreId: Int) = raw.update {
        val selected = it.selectedGenreIds
        it.copy(selectedGenreIds = if (genreId in selected) selected - genreId else selected + genreId)
    }

    fun onWatchlistSortChange(sort: WatchlistSort) = raw.update { it.copy(watchlistSort = sort) }

    fun onHistorySortChange(sort: HistorySort) = raw.update { it.copy(historySort = sort) }

    /** Tapping the same threshold again turns it off. */
    fun onMinMyRatingChange(minRating: Int?) = raw.update {
        it.copy(minMyRating = if (it.minMyRating == minRating) null else minRating)
    }

    // --- Rating edits ---

    /**
     * Changes the rating directly from the Watched tab (optimistically, like
     * MovieDetailViewModel.rateMovie). The movie is already watched here, so it is always a PUT
     * (updateHistoryItem), never a POST.
     */
    fun rateMovie(tmdbId: Int, rating: Int) {
        val userId = sessionManager.getUserId() ?: return
        val previousHistory = raw.value.history
        raw.update { state ->
            state.copy(history = state.history.map { if (it.tmdbId == tmdbId) it.copy(userRating = rating.toDouble()) else it })
        }

        historyJobs[tmdbId]?.cancel()
        historyJobs[tmdbId] = viewModelScope.launch {
            userRepository.updateHistoryItem(userId, tmdbId, rating.toDouble())
                .onSuccess { _events.tryEmit(LibraryEvent.Message(R.string.library_rating_saved)) }
                .onFailure { e ->
                    Log.w("LibraryViewModel", "rateMovie nije uspeo: ${e.message}")
                    raw.update { it.copy(history = previousHistory) }
                    _events.tryEmit(LibraryEvent.Message(R.string.library_rating_failed))
                }
        }
    }

    // --- Removing from the watchlist, with "Undo" ---

    /**
     * The item disappears from the list immediately and the screen shows "Removed · Undo". The
     * network call is sent only in [commitRemove] (when the snackbar expires or the user leaves the
     * screen), so "Undo" never needs to restore anything on the server - it just shows the item
     * again.
     */
    fun requestRemove(item: WatchlistItem) {
        raw.update { it.copy(pendingRemovalIds = it.pendingRemovalIds + item.tmdbId) }
        _events.tryEmit(LibraryEvent.WatchlistItemRemoved(item.tmdbId, item.title))
    }

    /** "Undo": shows the item again (nothing was sent to the server yet). */
    fun undoRemove(tmdbId: Int) {
        if (removalJobs.containsKey(tmdbId)) return // the network call has already started
        raw.update { it.copy(pendingRemovalIds = it.pendingRemovalIds - tmdbId) }
    }

    /** Actually removes the item on the server (the snackbar expired). */
    fun commitRemove(tmdbId: Int) {
        val userId = sessionManager.getUserId() ?: return
        if (tmdbId !in raw.value.pendingRemovalIds || removalJobs.containsKey(tmdbId)) return

        removalJobs[tmdbId] = viewModelScope.launch {
            userRepository.removeFromWatchlist(userId, tmdbId)
                .onFailure { e ->
                    Log.w("LibraryViewModel", "removeFromWatchlist nije uspeo: ${e.message}")
                    _events.tryEmit(LibraryEvent.Message(R.string.library_remove_failed))
                }
            // Success: the repository deleted the row from Room and the Flow no longer emits it.
            // Failure: the item reappears (the user was informed with a message above).
            raw.update { it.copy(pendingRemovalIds = it.pendingRemovalIds - tmdbId) }
            removalJobs.remove(tmdbId)
        }
    }

    // --- Removing from "Watched", with "Undo" (same mechanism as the watchlist) ---

    private val historyRemovalJobs = mutableMapOf<Int, Job>()

    fun requestRemoveFromHistory(item: HistoryItem) {
        raw.update { it.copy(pendingHistoryRemovalIds = it.pendingHistoryRemovalIds + item.tmdbId) }
        _events.tryEmit(LibraryEvent.HistoryItemRemoved(item.tmdbId, item.title))
    }

    fun undoRemoveFromHistory(tmdbId: Int) {
        if (historyRemovalJobs.containsKey(tmdbId)) return
        raw.update { it.copy(pendingHistoryRemovalIds = it.pendingHistoryRemovalIds - tmdbId) }
    }

    fun commitRemoveFromHistory(tmdbId: Int) {
        val userId = sessionManager.getUserId() ?: return
        if (tmdbId !in raw.value.pendingHistoryRemovalIds || historyRemovalJobs.containsKey(tmdbId)) return

        historyRemovalJobs[tmdbId] = viewModelScope.launch {
            userRepository.removeFromHistory(userId, tmdbId)
                .onFailure { e ->
                    Log.w("LibraryViewModel", "removeFromHistory nije uspeo: ${e.message}")
                    _events.tryEmit(LibraryEvent.Message(R.string.library_remove_failed))
                }
            raw.update { it.copy(pendingHistoryRemovalIds = it.pendingHistoryRemovalIds - tmdbId) }
            historyRemovalJobs.remove(tmdbId)
        }
    }

    /**
     * The user leaves the screen before the snackbar expires - removals that were not undone are
     * carried out.
     */
    fun commitAllPendingRemovals() {
        raw.value.pendingRemovalIds.forEach { commitRemove(it) }
        raw.value.pendingHistoryRemovalIds.forEach { commitRemoveFromHistory(it) }
    }

    // --- Deriving the UI state ---

    private fun LibraryRawState.toUiState(): LibraryUiState {
        val q = query.trim()
        fun matches(title: String, genres: List<Genre>?): Boolean =
            (q.isEmpty() || title.contains(q, ignoreCase = true)) &&
                (selectedGenreIds.isEmpty() || genres.orEmpty().any { it.id in selectedGenreIds })

        val visibleWatchlist = watchlist.filter { it.tmdbId !in pendingRemovalIds }
        val filteredWatchlist = visibleWatchlist
            .filter { matches(it.title, it.genres) }
            .let { list ->
                when (watchlistSort) {
                    WatchlistSort.ADDED -> list.sortedByDescending { it.addedAt }
                    WatchlistSort.TMDB_RATING -> list.sortedByDescending { it.voteAverage ?: -1.0 }
                    WatchlistSort.TITLE -> list.sortedBy { it.title.lowercase() }
                    WatchlistSort.YEAR -> list.sortedByDescending { it.releaseYear ?: Int.MIN_VALUE }
                }
            }

        val visibleHistory = history.filter { it.tmdbId !in pendingHistoryRemovalIds }
        val filteredHistory = visibleHistory
            .filter { matches(it.title, it.genres) }
            .filter { item -> minMyRating == null || item.userRating >= minMyRating }
            .let { list ->
                when (historySort) {
                    HistorySort.WATCHED -> list.sortedByDescending { it.watchedAt }
                    HistorySort.MY_RATING -> list.sortedByDescending { it.userRating }
                    HistorySort.TMDB_RATING -> list.sortedByDescending { it.voteAverage ?: -1.0 }
                    HistorySort.TITLE -> list.sortedBy { it.title.lowercase() }
                }
            }

        val availableGenres = (visibleWatchlist.flatMap { it.genres.orEmpty() } + visibleHistory.flatMap { it.genres.orEmpty() })
            .distinctBy { it.id }
            .sortedBy { it.name }

        return LibraryUiState(
            watchlist = filteredWatchlist,
            history = filteredHistory,
            watchlistTotal = visibleWatchlist.size,
            historyTotal = visibleHistory.size,
            availableGenres = availableGenres,
            query = query,
            selectedGenreIds = selectedGenreIds,
            watchlistSort = watchlistSort,
            historySort = historySort,
            minMyRating = minMyRating,
            isLoading = isLoading,
            requiresSignIn = requiresSignIn,
            error = error
        )
    }
}
