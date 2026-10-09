package dev.milinko.movielens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.UserRepository
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the Home screen: every row, its loading flags and paging. */
data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedImageUri: String? = null,
    val visionResult: VisionSearchResponse? = null,
    val trendingMovies: List<MovieSearchResult> = emptyList(),
    // Separate from isLoading (image recognition), so neither blocks the other.
    val isTrendingLoading: Boolean = false,
    // Infinite scroll - separate from isTrendingLoading (first page / full-list spinner), so the
    // footer spinner shows only at the bottom of the list.
    val isLoadingMore: Boolean = false,
    // false when the last page came back empty - no point requesting more.
    val canLoadMore: Boolean = true,
    // "Suggested for you" - personalized by the genre taste profile, deliberately separate from
    // trendingMovies (always plain TMDB popular). Empty when signed out.
    val personalizedMovies: List<MovieSearchResult> = emptyList(),
    // "In cinemas now" row - extra content; silently stays empty if the call fails.
    val nowInCinemas: List<NowInCinemasMovie> = emptyList(),
    // "Continue watching" - from the Room cache (same source as Library); empty when signed out or
    // when the watchlist is empty.
    val watchlistPreview: List<WatchlistItem> = emptyList(),
    // "Friends are watching" - the latest movies friends watched/rated (same activity cache as the
    // Friends screen). Empty for guests or users without friends - the row is then hidden.
    val friendsActivity: List<FriendActivity> = emptyList(),
    // Pull-to-refresh - separate from isTrendingLoading to avoid duplicate spinners
    // (PullToRefreshBox has its own indicator driven by this flag).
    val isRefreshing: Boolean = false,
    val error: String? = null
)

/**
 * Home screen data: popular movies (infinite scroll), personalized suggestions, the cinema row,
 * the watchlist preview and friends' activity. Most rows are optional extras: their failures are
 * silent and never break the rest of the screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userRepository: UserRepository,
    private val visionRepository: VisionRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Current trending page - not in uiState because only loadMoreTrending() needs it.
    private var currentPage = 1

    // Current "Suggested for you" page - pull-to-refresh cycles 1..MAX_PERSONALIZED_PAGE and starts
    // over, like "Refresh suggestions" on Movie Night (MovieNightViewModel).
    private var personalizedPage = 1

    private companion object {
        const val MAX_PERSONALIZED_PAGE = 5
        const val HOME_NOW_IN_CINEMAS_LIMIT = 20
        const val HOME_FRIENDS_ACTIVITY_LIMIT = 10
    }

    init {
        loadTrendingMovies()
        loadPersonalizedMovies()
        loadNowInCinemas()
        observeWatchlistPreview()
        observeFriendsActivity()
    }

    /**
     * Called every time Home is shown. HomeViewModel lives for the whole session (Home is the start
     * destination), so init{} runs only once; if that first load failed (e.g. a cold backend) the
     * list would stay empty forever without this. Does nothing if data exists or a load is running.
     */
    fun ensureTrendingLoaded() {
        if (_uiState.value.trendingMovies.isEmpty() && !_uiState.value.isTrendingLoading) {
            loadTrendingMovies()
        }
        if (sessionManager.getUserId() != null && _uiState.value.personalizedMovies.isEmpty()) {
            loadPersonalizedMovies()
        }
    }

    // Image recognition (legacy - the Search screen uses SearchViewModel for this; HomeScreen does
    // not call these).
    fun onImageSelected(uri: String?) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            visionResult = null,
            error = null
        )
    }

    fun detectImage() {
        val imageUri = _uiState.value.selectedImageUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = visionRepository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    visionResult = response,
                    selectedImageUri = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_image_analysis)
                )
            }
        }
    }

    /**
     * "Popular movies" - ALWAYS plain TMDB popular, regardless of sign-in. Personalization is a
     * separate row, see loadPersonalizedMovies().
     */
    private fun loadTrendingMovies() {
        currentPage = 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTrendingLoading = true)
            movieRepository.getTrendingMovies(currentPage)
                .onSuccess { movies ->
                    _uiState.value = _uiState.value.copy(
                        isTrendingLoading = false,
                        trendingMovies = movies,
                        canLoadMore = movies.isNotEmpty()
                    )
                }
                .onFailure {
                    // `error` is not set (that field belongs to the image recognition UI); only the
                    // loading flag is cleared so ensureTrendingLoaded() can retry when the user
                    // returns to Home.
                    _uiState.value = _uiState.value.copy(isTrendingLoading = false)
                }
        }
    }

    /**
     * Infinite scroll - called when the user nears the bottom of the list. Does nothing while a load
     * is running or after an empty last page (canLoadMore = false, likely the end of TMDB results).
     */
    fun loadMoreTrending() {
        val state = _uiState.value
        if (state.isTrendingLoading || state.isLoadingMore || !state.canLoadMore) return

        val nextPage = currentPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            movieRepository.getTrendingMovies(nextPage)
                .onSuccess { movies ->
                    currentPage = nextPage
                    // Dedupe by tmdbId - TMDB pages can overlap if the popularity order changes
                    // between two calls.
                    val existingIds = _uiState.value.trendingMovies.map { it.tmdbId }.toSet()
                    val newMovies = movies.filter { it.tmdbId !in existingIds }
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        trendingMovies = _uiState.value.trendingMovies + newMovies,
                        canLoadMore = movies.isNotEmpty()
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    /**
     * "Suggested for you" - signed-in users only. The backend falls back to popular movies until the
     * user has enough ratings for a taste profile (see MovieRouting.kt /featured), so early on this
     * row can look like "Popular". One page (~20 movies), no paging - it is a horizontal row.
     */
    private fun loadPersonalizedMovies() {
        val userId = sessionManager.getUserId() ?: return
        personalizedPage = 1
        viewModelScope.launch {
            movieRepository.getFeaturedMovies(userId, page = personalizedPage).onSuccess { movies ->
                _uiState.value = _uiState.value.copy(personalizedMovies = movies)
            }
        }
    }

    /**
     * "In cinemas now" row - errors are ignored (extra content). The backend returns the WHOLE
     * programme (for the "See all" screen); the row shows only the first 20.
     */
    private fun loadNowInCinemas() {
        viewModelScope.launch {
            movieRepository.getNowInCinemas().onSuccess { movies ->
                _uiState.value = _uiState.value.copy(nowInCinemas = movies.take(HOME_NOW_IN_CINEMAS_LIMIT))
            }
        }
    }

    /**
     * "Continue watching" row - read from the Room cache (observeWatchlist), same source as Library,
     * instant and offline. It is also refreshed once from the network (written through to Room) so it
     * is not stale if Library has not been opened in this session.
     */
    private fun observeWatchlistPreview() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.observeWatchlist(userId).collect { watchlist ->
                _uiState.value = _uiState.value.copy(watchlistPreview = watchlist.take(10))
            }
        }
        viewModelScope.launch {
            userRepository.getWatchlist(userId)
        }
    }

    private fun observeFriendsActivity() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.observeCachedFriendActivity(userId).collect { activity ->
                // One card per movie (the latest activity), so one movie watched by several friends
                // does not fill the whole row.
                _uiState.value = _uiState.value.copy(
                    friendsActivity = activity
                        .sortedByDescending { it.timestamp }
                        .distinctBy { it.tmdbId }
                        .take(HOME_FRIENDS_ACTIVITY_LIMIT)
                )
            }
        }
        viewModelScope.launch {
            userRepository.getFriendActivity(userId)
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(visionResult = null, error = null)
    }

    /**
     * Pull-to-refresh on Home. Deliberately changes only "Suggested for you" (cycles the TMDB discover
     * page 1..MAX_PERSONALIZED_PAGE, like "Refresh suggestions" on Movie Night): "Popular" and "In
     * cinemas now" do not change on demand, so refetching them would show the same content. Signed out
     * (no personalization), the gesture still shows the indicator briefly instead of feeling dead.
     */
    fun refreshAll() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
                delay(300)
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
            return
        }

        val nextPage = if (personalizedPage >= MAX_PERSONALIZED_PAGE) 1 else personalizedPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            movieRepository.getFeaturedMovies(userId, page = nextPage).onSuccess { movies ->
                personalizedPage = nextPage
                _uiState.value = _uiState.value.copy(personalizedMovies = movies)
            }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}

