package dev.milinko.movielens.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.RecentSearchesStore
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the Search screen for both modes (text/image search and "In cinemas"). */
data class SearchUiState(
    val searchQuery: String = "",
    // Separate from isAnalyzingImage, so typing while an image is selected never shows
    // "Analyzing image..." when no analysis is running.
    val isSearching: Boolean = false,
    val isAnalyzingImage: Boolean = false,
    val selectedImageUri: String? = null,
    val searchResults: List<MovieSearchResult> = emptyList(),
    val visionResult: VisionSearchResponse? = null,
    val error: String? = null,
    // Distinguishes a real error (red card) from a temporary state that is not the user's fault,
    // such as Gemini overload (a calmer info card). See TransientServiceException.kt and
    // SearchScreen.
    val isSoftError: Boolean = false,
    // "In cinemas" mode: search limited to the movies currently showing (see the backend's
    // CinemaSemanticIndex), with its own result type (NowInCinemasMovie). `cinemaResults` is
    // updated in two ways: (1) a debounced local title filter over `allCinemaMovies` while typing
    // (no network, free - see setSearchQuery), (2) the real semantic call ONLY on an explicit
    // submit (see searchCinemasSemantic/searchMovies).
    val isCinemaMode: Boolean = false,
    val cinemaResults: List<NowInCinemasMovie> = emptyList(),
    val isSearchingCinemas: Boolean = false,
    // True while the 300ms debounce waits before local filtering; without it the "no matches"
    // message would flicker on every character before the filter runs.
    val isFilteringCinemasLive: Boolean = false,
    // The whole current programme, fetched ONCE when entering the "In cinemas" mode (a plain call
    // without embedding cost, same data as the Home row); the local filter works on this list.
    val allCinemaMovies: List<NowInCinemasMovie> = emptyList(),
    // The programme could not be loaded - without this every search would show "No title matches",
    // as if the movie did not exist.
    val cinemaCatalogError: String? = null,
    // Which embedding provider the backend uses for the "In cinemas" semantic search (Gemini by
    // default; local MiniLM for comparison). Visible only in the cinema mode, see SearchScreen.
    val useLocalEmbedding: Boolean = false
)

/**
 * Search: debounced text search, image recognition and the "In cinemas" mode (live local title
 * filter while typing + semantic search on submit), plus the per-account recent searches.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val visionRepository: VisionRepository,
    private val recentSearchesStore: RecentSearchesStore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // The history is per account (see RecentSearchesStore.kt), "guest" when signed out (text search
    // works without signing in). Reading the scope once is enough: sign-out clears the whole back
    // stack, so a new account always gets a new instance of this ViewModel.
    private val recentSearchesScopeId = sessionManager.getUserId() ?: "guest"

    // Separate from SearchUiState because it depends on no network call - it only reads and writes
    // SharedPreferences.
    private val _recentSearches = MutableStateFlow(recentSearchesStore.getQueries(recentSearchesScopeId))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // Debounce job for the search while typing - each new character cancels the previous one.
    private var searchJob: Job? = null

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
    }

    /**
     * Updates the query and schedules a debounced live search (or the local filter in the cinema
     * mode).
     */
    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        // In the "In cinemas" mode typing only runs the local title filter over `allCinemaMovies`
        // (debounced, no network): each semantic query embeds text via Gemini, which costs money,
        // so the real semantic call happens only on an explicit submit (searchMovies below).
        if (_uiState.value.isCinemaMode) {
            filterCinemaResultsLive(query)
            return
        }

        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), error = null, isSoftError = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(trimmed)
        }
    }

    /**
     * Debounced local title filter over `allCinemaMovies` (no network), with the same debounce
     * (300ms) and minimum length as the text search, so typing feels the same in both modes.
     */
    private fun filterCinemaResultsLive(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(cinemaResults = emptyList(), isFilteringCinemasLive = false, error = null, isSoftError = false)
            return
        }
        _uiState.value = _uiState.value.copy(isFilteringCinemasLive = true)
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val filtered = _uiState.value.allCinemaMovies.filter { it.title.contains(trimmed, ignoreCase = true) }
            _uiState.value = _uiState.value.copy(cinemaResults = filtered, isFilteringCinemasLive = false, error = null, isSoftError = false)
        }
    }

    /**
     * Switches between the text search (whole TMDB catalog) and the semantic search over the movies
     * currently in cinemas. Clears the results of BOTH modes, so results of a different type never
     * show under the new mode, and immediately re-runs the entered text in the new mode.
     */
    fun setCinemaMode(enabled: Boolean) {
        if (_uiState.value.isCinemaMode == enabled) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isCinemaMode = enabled,
            searchResults = emptyList(),
            cinemaResults = emptyList(),
            // searchJob?.cancel() above stops the debounce job before it can reset this flag, so it
            // would stay stuck on true if the mode changed in the middle of the debounce window.
            isFilteringCinemasLive = false,
            visionResult = null,
            error = null,
            isSoftError = false,
            // There is no image search in the "In cinemas" mode, so a previously picked image is
            // removed - otherwise submit would silently run the image analysis instead of the
            // semantic search.
            selectedImageUri = if (enabled) null else _uiState.value.selectedImageUri
        )
        // The programme is fetched ONCE per mode entry (not per character), so the local filter has
        // data. If it is already loaded (e.g. the user left and came back), it is not refetched.
        if (enabled && _uiState.value.allCinemaMovies.isEmpty()) {
            loadCinemaCatalog()
        }
        setSearchQuery(_uiState.value.searchQuery)
    }

    /** "Try again" when the programme for the "In cinemas" mode failed to load. */
    fun retryCinemaCatalog() = loadCinemaCatalog()

    private var cinemaCatalogJob: Job? = null

    /**
     * Fetches the whole current programme (same data as the Home row, no embedding cost) for the
     * local title filter. A failure sets cinemaCatalogError; the explicit semantic search still
     * works independently with its own call.
     */
    private fun loadCinemaCatalog() {
        cinemaCatalogJob?.cancel()
        cinemaCatalogJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(cinemaCatalogError = null)
            movieRepository.getNowInCinemas().onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    cinemaCatalogError = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_cinemas_unavailable)
                )
            }.onSuccess { movies ->
                _uiState.value = _uiState.value.copy(allCinemaMovies = movies)
                // If the user typed something while the programme was loading, apply the filter
                // now instead of waiting for the next character.
                val query = _uiState.value.searchQuery.trim()
                if (query.length >= MIN_QUERY_LENGTH) {
                    _uiState.value = _uiState.value.copy(
                        cinemaResults = movies.filter { it.title.contains(query, ignoreCase = true) }
                    )
                }
            }
        }
    }

    /**
     * Immediate search on Enter / the IME search action - skips the debounce. Recorded in the
     * history (an explicit action, see [recordCurrentQueryInHistory]). In the "In cinemas" mode it
     * delegates to [searchCinemasSemantic]; the history is kept only for the text mode.
     */
    fun searchMovies() {
        if (_uiState.value.isCinemaMode) {
            searchCinemasSemantic()
            return
        }

        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        _recentSearches.value = recentSearchesStore.addQuery(recentSearchesScopeId, query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    /**
     * Switches the embedding provider of the "In cinemas" search (Gemini / local MiniLM). Clears
     * the old results: another provider means another vector space.
     */
    fun setUseLocalEmbedding(enabled: Boolean) {
        if (_uiState.value.useLocalEmbedding == enabled) return
        _uiState.value = _uiState.value.copy(useLocalEmbedding = enabled, cinemaResults = emptyList())
    }

    private fun searchCinemasSemantic() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        val engine = if (_uiState.value.useLocalEmbedding) "local" else "gemini"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingCinemas = true, error = null, isSoftError = false)
            movieRepository.semanticSearchNowInCinemas(query, engine)
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingCinemas = false,
                        cinemaResults = results
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingCinemas = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_search),
                        isSoftError = false
                    )
                }
        }
    }

    /**
     * Runs the search for a query from the history (tapping a chip) - skips the debounce, like
     * Enter.
     */
    fun selectRecentQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        _recentSearches.value = recentSearchesStore.addQuery(recentSearchesScopeId, query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    /** Removes one query from the history. */
    fun removeRecentQuery(query: String) {
        _recentSearches.value = recentSearchesStore.removeQuery(recentSearchesScopeId, query)
    }

    /** Clears the whole search history of this account. */
    fun clearRecentQueries() {
        _recentSearches.value = recentSearchesStore.clearQueries(recentSearchesScopeId)
    }

    /**
     * Records the current query in the history. Called by SearchScreen when the user actually opens
     * a result (a clear sign that the search was useful); the debounced live search does not record
     * anything, otherwise every pause in typing would be saved. No-op for an empty query (e.g. a
     * pure image search).
     */
    fun recordCurrentQueryInHistory() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isNotEmpty()) {
            _recentSearches.value = recentSearchesStore.addQuery(recentSearchesScopeId, query)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, error = null, isSoftError = false)
        movieRepository.searchMovies(query)
            .onSuccess { results ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = results
                )
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_search),
                    isSoftError = false
                )
            }
    }

    /** Sets the picked image (null removes it). */
    fun onImageSelected(uri: String?) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            visionResult = null,
            error = null,
            isSoftError = false
        )
    }

    /** Sends the picked image for recognition (Gemini vision via the backend). */
    fun detectImage() {
        val imageUri = _uiState.value.selectedImageUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzingImage = true, error = null, isSoftError = false)
            try {
                val response = visionRepository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    visionResult = response,
                    selectedImageUri = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: dev.milinko.movielens.util.TransientServiceException) {
                // Gemini overload and similar "not your fault" states get a calmer UI instead of a
                // red error (see TransientServiceException.kt / VisionRepository.kt).
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    error = e.message,
                    isSoftError = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_image_analysis),
                    isSoftError = false
                )
            }
        }
    }

    /** Clears the query and all results (text, image and cinema) and any error. */
    fun clearResults() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            visionResult = null,
            searchQuery = "",
            error = null,
            isSoftError = false,
            cinemaResults = emptyList(),
            isFilteringCinemasLive = false
        )
    }
}

