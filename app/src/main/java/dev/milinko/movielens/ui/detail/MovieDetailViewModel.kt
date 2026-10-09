package dev.milinko.movielens.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.location.LocationHelper
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieComment
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the movie detail screen, including the signed-in user's own data for the movie. */
data class MovieDetailUiState(
    val movieDetail: MovieDetail? = null,
    val isLoading: Boolean = false,
    val isInWatchlist: Boolean = false,
    val isWatched: Boolean = false,
    // 0.0 = watched without a rating (same convention as the backend, see UserActionsRouting.kt)
    val userRating: Double = 0.0,
    // Friends who watched this movie (with their rating, if any). Silently empty when signed out or
    // when the call fails - not critical, it never blocks the screen.
    val friendsWatched: List<FriendWatched> = emptyList(),
    // Public comments, independent of the rating and watched status. `comments` are ALL comments
    // loaded so far; `commentsVisibleCount` is how many of them are shown. "Show more" first reveals
    // already loaded ones (no network) and fetches the next page only when they run out and
    // `commentsHasMoreOnServer` is true. "Show less" only resets the visible count to
    // COMMENTS_PAGE_SIZE without dropping the loaded comments.
    val comments: List<MovieComment> = emptyList(),
    val commentsVisibleCount: Int = 0,
    // Text of a comment the server rejected - the screen puts it back into the field and shows a
    // message (optimistic posting, see postComment).
    val failedCommentText: String? = null,
    val commentsHasMoreOnServer: Boolean = false,
    val isLoadingComments: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    // Guests see the comments, but get a sign-in prompt instead of the input field.
    val isSignedIn: Boolean = false,
    // "Send to a friend" - the friend list is loaded only when the dialog opens.
    val shareFriends: List<FriendInfo> = emptyList(),
    val isLoadingShareFriends: Boolean = false,
    val isSendingShare: Boolean = false,
    val error: String? = null
)

/**
 * Movie detail: loads the movie (cinemas sorted by distance when location is available), the user's
 * watchlist/watched/rating state (cache first, then network), friends who watched it and comments,
 * and handles the user's actions on the movie.
 */
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    // toggleWatched/rateMovie modify the SAME backend row (history), so they share one Job;
    // toggleWatchlist is a separate resource with its own Job. Each new action CANCELS the previous
    // one if it is still in flight (Retrofit suspend calls honor cancellation, so the HTTP call is
    // aborted too). Rapid repeated taps on the stars/buttons therefore send only the last request,
    // with no race between them.
    private var historyJob: Job? = null
    private var watchlistJob: Job? = null

    /** Loads everything for the movie; cached user data is shown first, then refreshed. */
    fun loadMovieDetail(tmdbId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Last known location, only if permission is already granted (the UI asks for it).
            // Without it cinemas are simply not sorted by distance.
            val location = locationHelper.getLastKnownLocation()
            val userId = sessionManager.getUserId()
            _uiState.value = _uiState.value.copy(isSignedIn = userId != null)

            // Show the user's watchlist/history state from the Room cache first (instant, works
            // offline); the network calls below refresh it and the onSuccess blocks overwrite it
            // with fresher values.
            if (userId != null) {
                val cachedHistoryItem = userRepository.observeHistory(userId).first().firstOrNull { it.tmdbId == tmdbId }
                if (cachedHistoryItem != null) {
                    _uiState.value = _uiState.value.copy(
                        isWatched = true,
                        userRating = cachedHistoryItem.userRating
                    )
                }
                val isCachedInWatchlist = userRepository.observeWatchlist(userId).first().any { it.tmdbId == tmdbId }
                if (isCachedInWatchlist) {
                    _uiState.value = _uiState.value.copy(isInWatchlist = true)
                }
            }

            // Details, watchlist and history are independent calls, so they run in parallel instead
            // of three round trips in a row.
            val detailDeferred = async { movieRepository.getMovieDetail(tmdbId, location?.latitude, location?.longitude) }
            val watchlistDeferred = userId?.let { async { userRepository.getWatchlist(it) } }
            val historyDeferred = userId?.let { async { userRepository.getHistory(it) } }
            val friendsWatchedDeferred = userId?.let { async { movieRepository.getFriendsWhoWatched(tmdbId) } }

            detailDeferred.await()
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movieDetail = detail
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_load_movie)
                    )
                }

            // There is no "is this movie on my list" endpoint, so the full lists are checked - one
            // call each per opening of the screen, and the state is accurate instead of always
            // starting from "not on the watchlist / not watched".
            watchlistDeferred?.await()?.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    isInWatchlist = items.any { it.tmdbId == tmdbId }
                )
            }
            historyDeferred?.await()?.onSuccess { items ->
                val item = items.firstOrNull { it.tmdbId == tmdbId }
                if (item != null) {
                    _uiState.value = _uiState.value.copy(isWatched = true, userRating = item.userRating)
                }
            }
            friendsWatchedDeferred?.await()?.onSuccess { friends ->
                _uiState.value = _uiState.value.copy(friendsWatched = friends)
            }

            loadComments(tmdbId)
        }
    }

    /**
     * Toggles "watched". A second tap (already watched) is an undo for an accidental tap and also
     * removes the rating (same row on the backend). Returns true if the request was started, false
     * if the user is not signed in.
     */
    fun toggleWatched(tmdbId: Int): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val wasWatched = _uiState.value.isWatched
        val previousRating = _uiState.value.userRating
        _uiState.value = _uiState.value.copy(
            isWatched = !wasWatched,
            userRating = if (wasWatched) 0.0 else previousRating
        )

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.removeFromHistory(userId, tmdbId)
            } else {
                userRepository.addToHistory(userId, tmdbId).map { }
            }
            // Not shown to the user (see historyJob): the usual cause is a cancelled request from
            // rapid taps, not a real problem. It is logged and the local state reverts to the last
            // known good value; the next successful call or screen opening resyncs everything.
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "toggleWatched nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isWatched = wasWatched,
                    userRating = previousRating
                )
            }
        }
        return true
    }

    /**
     * Sets or changes the rating (1-10) and marks the movie as watched if it is not already. Returns
     * true if the request was started, false if the user is not signed in.
     */
    fun rateMovie(tmdbId: Int, rating: Int): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val previousRating = _uiState.value.userRating
        val wasWatched = _uiState.value.isWatched
        _uiState.value = _uiState.value.copy(
            userRating = rating.toDouble(),
            isWatched = true
        )

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.updateHistoryItem(userId, tmdbId, rating.toDouble())
            } else {
                userRepository.addToHistory(userId, tmdbId, rating = rating.toDouble())
            }
            // Logged quietly instead of shown to the user (see historyJob/toggleWatched).
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "rateMovie nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(
                    userRating = previousRating,
                    isWatched = wasWatched
                )
            }
        }
        return true
    }

    /**
     * Adds/removes the movie on the watchlist. The UI updates immediately (optimistically) and
     * reverts if the call fails. Returns true if the user is NOT signed in (nothing was attempted) -
     * the screen should then ask for sign-in.
     */
    fun toggleWatchlist(tmdbId: Int): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val wasInWatchlist = _uiState.value.isInWatchlist
        _uiState.value = _uiState.value.copy(isInWatchlist = !wasInWatchlist)

        watchlistJob?.cancel()
        watchlistJob = viewModelScope.launch {
            val result = if (wasInWatchlist) {
                userRepository.removeFromWatchlist(userId, tmdbId).map { }
            } else {
                userRepository.addToWatchlist(userId, tmdbId).map { }
            }
            // Logged quietly, same as toggleWatched (see historyJob).
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "toggleWatchlist nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(isInWatchlist = wasInWatchlist)
            }
        }
        return true
    }

    // --- Public comments (see MovieDetailUiState.comments for the paging/reveal logic) ---

    private fun loadComments(tmdbId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingComments = true)
            movieRepository.getComments(tmdbId, offset = 0, limit = COMMENTS_PAGE_SIZE)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        comments = response.comments,
                        commentsVisibleCount = response.comments.size,
                        commentsHasMoreOnServer = response.hasMore,
                        isLoadingComments = false
                    )
                }
                .onFailure { e ->
                    // Quietly, like friendsWatched - comments are not critical and must not block
                    // the rest of the screen.
                    Log.w("MovieDetailViewModel", "loadComments nije uspeo (tiho): ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoadingComments = false)
                }
        }
    }

    /**
     * "Show more": first reveals already loaded comments (no call); only when they run out and the
     * server has more is the next page fetched.
     */
    fun showMoreComments(tmdbId: Int) {
        val state = _uiState.value
        if (state.commentsVisibleCount < state.comments.size) {
            _uiState.value = state.copy(
                commentsVisibleCount = (state.commentsVisibleCount + COMMENTS_PAGE_SIZE).coerceAtMost(state.comments.size)
            )
            return
        }
        if (!state.commentsHasMoreOnServer || state.isLoadingMoreComments) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMoreComments = true)
            movieRepository.getComments(tmdbId, offset = state.comments.size, limit = COMMENTS_PAGE_SIZE)
                .onSuccess { response ->
                    val merged = _uiState.value.comments + response.comments
                    _uiState.value = _uiState.value.copy(
                        comments = merged,
                        commentsVisibleCount = (_uiState.value.commentsVisibleCount + COMMENTS_PAGE_SIZE).coerceAtMost(merged.size),
                        commentsHasMoreOnServer = response.hasMore,
                        isLoadingMoreComments = false
                    )
                }
                .onFailure { e ->
                    Log.w("MovieDetailViewModel", "showMoreComments nije uspeo (tiho): ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoadingMoreComments = false)
                }
        }
    }

    /** "Show less": resets the visible count to the first page without dropping loaded comments. */
    fun showLessComments() {
        val state = _uiState.value
        _uiState.value = state.copy(commentsVisibleCount = COMMENTS_PAGE_SIZE.coerceAtMost(state.comments.size))
    }

    /**
     * Posts a new comment. It appears at the top of the list immediately (optimistically, before the
     * network confirms). Returns true if the request was started, false if the user is not signed in
     * or the text is empty.
     */
    fun postComment(tmdbId: Int, body: String): Boolean {
        val userId = sessionManager.getUserId() ?: return false
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false

        // Shown IMMEDIATELY with a temporary negative id until the server assigns the real one. On
        // failure it is removed and its text goes back into the field (failedCommentText).
        val tempId = nextTempCommentId--
        val optimistic = MovieComment(
            id = tempId,
            userId = userId,
            username = sessionManager.getUsername() ?: "",
            avatarUrl = sessionManager.getAvatarUrl(),
            body = trimmed,
            createdAt = System.currentTimeMillis(),
            isOwn = true
        )
        _uiState.value = _uiState.value.let { state ->
            state.copy(
                comments = listOf(optimistic) + state.comments,
                commentsVisibleCount = state.commentsVisibleCount + 1,
                failedCommentText = null
            )
        }

        viewModelScope.launch {
            movieRepository.addComment(tmdbId, trimmed)
                .onSuccess { comment ->
                    _uiState.value = _uiState.value.let { state ->
                        state.copy(comments = state.comments.map { if (it.id == tempId) comment else it })
                    }
                }
                .onFailure { e ->
                    Log.w("MovieDetailViewModel", "postComment nije uspeo, vraćam tekst u polje: ${e.message}")
                    _uiState.value = _uiState.value.let { state ->
                        state.copy(
                            comments = state.comments.filterNot { it.id == tempId },
                            commentsVisibleCount = (state.commentsVisibleCount - 1).coerceAtLeast(0),
                            failedCommentText = trimmed
                        )
                    }
                }
        }
        return true
    }

    /** The screen has put the failed text back into the field and shown the message. */
    fun consumeFailedComment() {
        _uiState.value = _uiState.value.copy(failedCommentText = null)
    }

    // Temporary (local) ids of optimistic comments - negative so they never clash with real ones.
    private var nextTempCommentId = -1

    /** Deletes one's own comment (optimistically, rolled back if the network refuses). */
    fun deleteComment(commentId: Int) {
        // Not on the server yet (temporary id) - nothing to delete there.
        if (commentId < 0) return
        val state = _uiState.value
        val index = state.comments.indexOfFirst { it.id == commentId }
        if (index == -1) return
        val removed = state.comments[index]
        _uiState.value = state.copy(
            comments = state.comments.filterNot { it.id == commentId },
            commentsVisibleCount = (state.commentsVisibleCount - 1).coerceAtLeast(0)
        )

        viewModelScope.launch {
            movieRepository.deleteComment(commentId).onFailure { e ->
                Log.w("MovieDetailViewModel", "deleteComment nije uspeo, vraćam komentar nazad: ${e.message}")
                val current = _uiState.value
                _uiState.value = current.copy(
                    comments = current.comments.toMutableList().apply { add(index.coerceAtMost(size), removed) },
                    commentsVisibleCount = current.commentsVisibleCount + 1
                )
            }
        }
    }

    /**
     * Likes/unlikes a comment: the heart and counter change immediately and are rolled back if the
     * network refuses (e.g. liking one's own comment, see CommentsRepository.toggleLike). It does not
     * share a Job with the others - likes on different comments are independent. Returns false if
     * the user is not signed in (nothing attempted) - the screen should ask for sign-in.
     */
    fun toggleCommentLike(commentId: Int): Boolean {
        if (sessionManager.getUserId() == null) return false
        if (commentId < 0) return true // temporary comment, not on the server yet
        val state = _uiState.value
        val comment = state.comments.firstOrNull { it.id == commentId } ?: return true
        val optimisticLiked = !comment.likedByMe
        val optimisticCount = (comment.likeCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0)
        _uiState.value = state.copy(
            comments = state.comments.map {
                if (it.id == commentId) it.copy(likedByMe = optimisticLiked, likeCount = optimisticCount) else it
            }
        )

        viewModelScope.launch {
            movieRepository.toggleCommentLike(commentId)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        comments = _uiState.value.comments.map {
                            if (it.id == commentId) it.copy(likedByMe = result.likedByMe, likeCount = result.likeCount) else it
                        }
                    )
                }
                .onFailure { e ->
                    Log.w("MovieDetailViewModel", "toggleCommentLike nije uspeo, vraćam nazad: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        comments = _uiState.value.comments.map {
                            if (it.id == commentId) it.copy(likedByMe = comment.likedByMe, likeCount = comment.likeCount) else it
                        }
                    )
                }
        }
        return true
    }

    /** Friends for the "Send to a friend" dialog: from the cache first (instant), then from the network. */
    fun loadShareFriends() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingShareFriends = true)
            val cached = userRepository.observeCachedFriends(userId).first()
            if (cached.isNotEmpty()) _uiState.value = _uiState.value.copy(shareFriends = cached)
            userRepository.getFriends(userId).onSuccess { friends ->
                _uiState.value = _uiState.value.copy(shareFriends = friends)
            }
            _uiState.value = _uiState.value.copy(isLoadingShareFriends = false)
        }
    }

    /** Sends the movie to a friend (push notification); [onResult] reports success. */
    fun shareWithFriend(tmdbId: Int, friendId: String, note: String?, onResult: (Boolean) -> Unit) {
        val userId = sessionManager.getUserId() ?: return onResult(false)
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingShare = true)
            val result = userRepository.shareMovie(userId, friendId, tmdbId, note)
            _uiState.value = _uiState.value.copy(isSendingShare = false)
            onResult(result.isSuccess)
        }
    }

    companion object {
        // Comments are loaded and revealed 3 at a time; "Show less" on the screen uses the same value.
        internal const val COMMENTS_PAGE_SIZE = 3
    }
}

