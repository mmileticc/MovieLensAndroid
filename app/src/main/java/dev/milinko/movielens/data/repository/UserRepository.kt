package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.local.GenericCacheDao
import dev.milinko.movielens.data.local.GenericCacheEntity
import dev.milinko.movielens.data.local.HistoryDao
import dev.milinko.movielens.data.local.HistoryEntity
import dev.milinko.movielens.data.local.WatchlistDao
import dev.milinko.movielens.data.local.WatchlistEntity
import dev.milinko.movielens.data.remote.user.AddToHistoryBody
import dev.milinko.movielens.data.remote.user.FriendActionBody
import dev.milinko.movielens.data.remote.user.FriendProfileResponse
import dev.milinko.movielens.data.remote.user.GenreAffinity
import dev.milinko.movielens.data.remote.user.NotificationItem
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.ShareMovieBody
import dev.milinko.movielens.data.remote.user.UpdateHistoryBody
import dev.milinko.movielens.data.remote.user.UserApiService
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.remote.user.MovieNightResponse
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.util.safeApiCall
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in user's data and social features: profile, watchlist, history, friends, activity
 * feed, Movie Night, sharing and notifications.
 *
 * Offline-first: `observe*` functions read only from Room and emit immediately, while the matching
 * `get*` calls fetch from the network and write the fresh data back into the same cache, so the
 * UI shows cached data at once and updates when the network answers.
 */
interface UserRepository {
    suspend fun getUserProfile(userId: String): Result<UserProfile>
    suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile>

    // Cache-only readers (never hit the network). The ViewModel shows these immediately while the
    // regular get* call below refreshes the same cache in the background.
    fun observeCachedUserProfile(userId: String): Flow<UserProfile?>
    fun observeCachedFriends(userId: String): Flow<List<FriendInfo>>
    fun observeCachedPendingRequests(userId: String): Flow<List<PendingRequest>>
    // Sent (outgoing) friend requests, same cache/network pattern as the incoming ones.
    fun observeCachedSentRequests(userId: String): Flow<List<PendingRequest>>
    fun observeCachedFriendActivity(userId: String): Flow<List<FriendActivity>>
    fun observeCachedTasteProfile(userId: String): Flow<List<GenreAffinity>>
    fun observeCachedFriendProfile(userId: String, friendId: String): Flow<FriendProfileResponse?>
    // Writes the friend profile to the cache only (no network). Called together with an optimistic
    // UI update (e.g. after sending or cancelling a friend request) so a freshly opened profile
    // screen does not briefly show the stale state from an older cache entry.
    suspend fun cacheFriendProfile(userId: String, friendId: String, profile: FriendProfileResponse)

    // Updates the friendship status inside an existing cached friend profile (no network), so an
    // action taken from the search results on the Profile screen is reflected on the friend's
    // profile screen and vice versa. If no cache entry exists there is nothing to update; the first
    // network load of that profile fills it in correctly.
    suspend fun updateCachedFriendshipStatus(userId: String, friendId: String, status: String)

    // Offline-first: read from Room immediately (also works without internet); getWatchlist
    // refreshes from the network and writes into the same table, and the Flow emits the new value.
    // Only watchlist and history have their own tables; search/trending/cinemas are not cached.
    fun observeWatchlist(userId: String): Flow<List<WatchlistItem>>
    suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>>
    suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem>
    suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit>

    fun observeHistory(userId: String): Flow<List<HistoryItem>>
    suspend fun getHistory(userId: String): Result<List<HistoryItem>>
    // rating/duration are optional: "watched without a rating" sends both as null (not 0.0, which
    // the backend would round to rating 1, see Double?.toRatingIntOrNull() there). `review` is a
    // short optional note attached to the rating.
    suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double? = null, review: String? = null, duration: Int? = null): Result<HistoryItem>
    // rating/review = null mean "keep the existing value" (e.g. edit only the note). See the
    // backend's UserActionsRepository.markWatched.
    suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double?, review: String? = null): Result<HistoryItem>
    // Undo for an accidental "mark as watched" - also removes the rating (same row on the backend).
    suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit>

    // User search for adding friends (search as you type).
    suspend fun searchUsers(query: String): Result<List<UserSearchResult>>
    suspend fun getPendingFriendRequests(userId: String): Result<List<PendingRequest>>
    suspend fun getSentFriendRequests(userId: String): Result<List<PendingRequest>>

    suspend fun getFriends(userId: String): Result<List<FriendInfo>>
    suspend fun addFriend(userId: String, friendId: String): Result<Unit>
    suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(userId: String, friendId: String): Result<Unit>
    suspend fun getFriendProfile(userId: String, friendId: String): Result<FriendProfileResponse>
    suspend fun getTasteProfile(userId: String): Result<List<GenreAffinity>>

    suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>>

    suspend fun shareMovie(userId: String, friendId: String, tmdbId: Int, note: String?): Result<Unit>

    suspend fun getNotifications(userId: String): Result<List<NotificationItem>>
    suspend fun markNotificationsRead(userId: String): Result<Unit>

    // Returns the whole MovieNightResponse (not just the list) so MovieNightViewModel also gets
    // `hasMore` (whether TMDB has more pages). forceRefresh bypasses the memory and Room cache
    // (pull-to-refresh and the silent refresh of the first page).
    suspend fun getMovieNightSuggestions(userId: String, friendId: String, page: Int = 1, forceRefresh: Boolean = false): Result<MovieNightResponse>

    /**
     * Loads Movie Night suggestions ahead of time (e.g. when a friend's profile opens) so that
     * getMovieNightSuggestions can return them instantly when the user taps the button. Errors are
     * swallowed: this is only an optimization.
     */
    suspend fun prefetchMovieNightSuggestions(userId: String, friendId: String)
}

private data class ApiErrorBody(val error: String? = null)

/** Extracts the message from the backend's `{"error": "..."}` error body (see ErrorResponse.kt). */
private fun Response<*>.parseErrorMessage(fallback: String): String {
    return try {
        val raw = errorBody()?.string()
        if (raw.isNullOrBlank()) fallback else Gson().fromJson(raw, ApiErrorBody::class.java)?.error ?: fallback
    } catch (e: Exception) {
        fallback
    }
}

// Genres of watchlist/history items are stored in Room as a JSON string (see WatchlistEntity).
private val genreListType = object : TypeToken<List<Genre>>() {}.type

private fun List<Genre>?.toGenresJson(): String? = this?.let { Gson().toJson(it) }

private fun String?.toGenres(): List<Genre>? = this?.let {
    try {
        Gson().fromJson<List<Genre>>(it, genreListType)
    } catch (e: Exception) {
        null
    }
}

private fun WatchlistEntity.toDomain() = WatchlistItem(
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    addedAt = addedAt,
    priority = priority,
    genres = genresJson.toGenres(),
    releaseYear = releaseYear,
    voteAverage = voteAverage
)

private fun WatchlistItem.toEntity(userId: String) = WatchlistEntity(
    userId = userId,
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    addedAt = addedAt,
    priority = priority,
    genresJson = genres.toGenresJson(),
    releaseYear = releaseYear,
    voteAverage = voteAverage
)

private fun HistoryEntity.toDomain() = HistoryItem(
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    userRating = userRating,
    review = review,
    watchedAt = watchedAt,
    duration = duration,
    genres = genresJson.toGenres(),
    releaseYear = releaseYear,
    voteAverage = voteAverage
)

private fun HistoryItem.toEntity(userId: String) = HistoryEntity(
    userId = userId,
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    userRating = userRating,
    review = review,
    watchedAt = watchedAt,
    duration = duration,
    genresJson = genres.toGenresJson(),
    releaseYear = releaseYear,
    voteAverage = voteAverage
)

/** Network + Room implementation of [UserRepository]; see the interface for the caching rules. */
@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao,
    private val genericCacheDao: GenericCacheDao
) : UserRepository {

    // In-memory cache of Movie Night suggestions ("userId:friendId:page" -> response), filled by
    // prefetchMovieNightSuggestions and read by getMovieNightSuggestions so MovieNightScreen does
    // not wait for the network. ConcurrentHashMap because a prefetch and a real call can overlap.
    // Room is the second level below it (memory is lost on every process restart).
    private val movieNightCache = java.util.concurrent.ConcurrentHashMap<String, MovieNightResponse>()

    // Shared Gson instance for GenericCache (de)serialization.
    private val cacheGson = Gson()

    private fun profileCacheKey(userId: String) = "profile:$userId"
    private fun friendsCacheKey(userId: String) = "friends:$userId"
    private fun pendingCacheKey(userId: String) = "pending:$userId"
    private fun sentCacheKey(userId: String) = "sent:$userId"
    private fun activityCacheKey(userId: String) = "activity:$userId"
    private fun tasteCacheKey(userId: String) = "taste:$userId"
    private fun friendProfileCacheKey(userId: String, friendId: String) = "friendprofile:$userId:$friendId"
    private fun movieNightCacheKey(userId: String, friendId: String, page: Int) = "movienight:$userId:$friendId:$page"

    override fun observeCachedUserProfile(userId: String): Flow<UserProfile?> =
        genericCacheDao.observe(profileCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json -> runCatching { cacheGson.fromJson(json, UserProfile::class.java) }.getOrNull() }
        }

    override fun observeCachedFriends(userId: String): Flow<List<FriendInfo>> =
        genericCacheDao.observe(friendsCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<FriendInfo>>(json, object : TypeToken<List<FriendInfo>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedPendingRequests(userId: String): Flow<List<PendingRequest>> =
        genericCacheDao.observe(pendingCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<PendingRequest>>(json, object : TypeToken<List<PendingRequest>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedSentRequests(userId: String): Flow<List<PendingRequest>> =
        genericCacheDao.observe(sentCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<PendingRequest>>(json, object : TypeToken<List<PendingRequest>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedFriendActivity(userId: String): Flow<List<FriendActivity>> =
        genericCacheDao.observe(activityCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<FriendActivity>>(json, object : TypeToken<List<FriendActivity>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedTasteProfile(userId: String): Flow<List<GenreAffinity>> =
        genericCacheDao.observe(tasteCacheKey(userId)).map { entity ->
            entity?.jsonPayload?.let { json ->
                runCatching { cacheGson.fromJson<List<GenreAffinity>>(json, object : TypeToken<List<GenreAffinity>>() {}.type) }.getOrNull()
            } ?: emptyList()
        }

    override fun observeCachedFriendProfile(userId: String, friendId: String): Flow<FriendProfileResponse?> =
        genericCacheDao.observe(friendProfileCacheKey(userId, friendId)).map { entity ->
            entity?.jsonPayload?.let { json -> runCatching { cacheGson.fromJson(json, FriendProfileResponse::class.java) }.getOrNull() }
                // Old cache entries without friendshipStatus (Gson leaves it null, see
                // UserModels.kt) are treated as missing: showing them would briefly render the
                // "not friends" locked view before the network corrects it. A spinner is better.
                ?.takeIf { it.friendshipStatus != null }
        }

    override suspend fun getUserProfile(userId: String): Result<UserProfile> = safeApiCall {
        val response = userApiService.getUserProfile(userId)
        if (response.isSuccessful) {
            val profile = response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
            genericCacheDao.put(GenericCacheEntity(profileCacheKey(userId), cacheGson.toJson(profile)))
            profile
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile> = safeApiCall {
        val response = userApiService.updateUserProfile(userId, profile)
        if (response.isSuccessful) {
            val updated = response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
            // Also written to the cache (not only on GET) so observeCachedUserProfile reflects the
            // change immediately.
            genericCacheDao.put(GenericCacheEntity(profileCacheKey(userId), cacheGson.toJson(updated)))
            updated
        } else {
            // The backend returns a specific message (e.g. "Username is already taken") in the
            // error body; it is shown to the user because, unlike other failures here, it is
            // actionable.
            throw IllegalStateException(response.parseErrorMessage("Ažuriranje profila nije uspelo (${response.code()})"))
        }
    }

    override fun observeWatchlist(userId: String): Flow<List<WatchlistItem>> =
        watchlistDao.observeWatchlist(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>> = safeApiCall {
        val response = userApiService.getWatchlist(userId)
        if (response.isSuccessful) {
            val items = response.body()?.items ?: emptyList()
            // Refresh the local cache; observeWatchlist's Flow emits the new value to every
            // collector (Room is the single source of truth for the UI).
            watchlistDao.replaceAll(userId, items.map { it.toEntity(userId) })
            items
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem> = safeApiCall {
        val response = userApiService.addToWatchlist(userId, mapOf("tmdbId" to tmdbId))
        if (response.isSuccessful) {
            val item = response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
            watchlistDao.insert(item.toEntity(userId))
            item
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromWatchlist(userId, tmdbId)
        if (response.isSuccessful) {
            watchlistDao.delete(userId, tmdbId)
        } else {
            throw HttpException(response)
        }
    }

    override fun observeHistory(userId: String): Flow<List<HistoryItem>> =
        historyDao.observeHistory(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getHistory(userId: String): Result<List<HistoryItem>> = safeApiCall {
        val response = userApiService.getHistory(userId)
        if (response.isSuccessful) {
            val items = response.body()?.items ?: emptyList()
            historyDao.replaceAll(userId, items.map { it.toEntity(userId) })
            items
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double?, review: String?, duration: Int?): Result<HistoryItem> = safeApiCall {
        // null rating/duration serialize as JSON null; the backend treats that the same as a
        // missing field.
        val response = userApiService.addToHistory(userId, AddToHistoryBody(tmdbId, rating, review, duration))
        if (response.isSuccessful) {
            val item = response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
            historyDao.insert(item.toEntity(userId))
            item
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double?, review: String?): Result<HistoryItem> = safeApiCall {
        val response = userApiService.updateHistoryItem(userId, tmdbId, UpdateHistoryBody(rating, review))
        if (response.isSuccessful) {
            val item = response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
            historyDao.insert(item.toEntity(userId))
            item
        } else if (response.code() == 404) {
            // Fallback: the local state thought the movie was already watched (e.g. a stale
            // cache) and called PUT; if the backend answers 404, try POST instead of reporting an
            // error to the user.
            val addResponse = userApiService.addToHistory(userId, AddToHistoryBody(tmdbId, rating, review, null))
            if (addResponse.isSuccessful) {
                val item = addResponse.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
                historyDao.insert(item.toEntity(userId))
                item
            } else {
                throw HttpException(addResponse)
            }
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromHistory(userId, tmdbId)
        if (response.isSuccessful) {
            historyDao.delete(userId, tmdbId)
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun searchUsers(query: String): Result<List<UserSearchResult>> = safeApiCall {
        val response = userApiService.searchUsers(query)
        if (response.isSuccessful) {
            response.body()?.users ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getPendingFriendRequests(userId: String): Result<List<PendingRequest>> = safeApiCall {
        val response = userApiService.getPendingFriendRequests(userId)
        if (response.isSuccessful) {
            val requests = response.body()?.requests ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(pendingCacheKey(userId), cacheGson.toJson(requests)))
            requests
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getSentFriendRequests(userId: String): Result<List<PendingRequest>> = safeApiCall {
        val response = userApiService.getSentFriendRequests(userId)
        if (response.isSuccessful) {
            val requests = response.body()?.requests ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(sentCacheKey(userId), cacheGson.toJson(requests)))
            requests
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getFriends(userId: String): Result<List<FriendInfo>> = safeApiCall {
        val response = userApiService.getFriends(userId)
        if (response.isSuccessful) {
            val friends = response.body()?.friends ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(friendsCacheKey(userId), cacheGson.toJson(friends)))
            friends
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getNotifications(userId: String): Result<List<NotificationItem>> = safeApiCall {
        val response = userApiService.getNotifications(userId)
        if (!response.isSuccessful) throw HttpException(response)
        response.body()?.notifications.orEmpty()
    }

    override suspend fun markNotificationsRead(userId: String): Result<Unit> = safeApiCall {
        val response = userApiService.markNotificationsRead(userId)
        if (!response.isSuccessful) throw HttpException(response)
    }

    override suspend fun shareMovie(userId: String, friendId: String, tmdbId: Int, note: String?): Result<Unit> = safeApiCall {
        val response = userApiService.shareMovie(userId, ShareMovieBody(friendId, tmdbId, note))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun addFriend(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("add"))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("accept"))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("reject"))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun removeFriend(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.removeFriend(userId, friendId)
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    override suspend fun getFriendProfile(userId: String, friendId: String): Result<FriendProfileResponse> = safeApiCall {
        val response = userApiService.getFriendProfile(userId, friendId)
        if (response.isSuccessful) {
            val profile = response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
            genericCacheDao.put(GenericCacheEntity(friendProfileCacheKey(userId, friendId), cacheGson.toJson(profile)))
            profile
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun cacheFriendProfile(userId: String, friendId: String, profile: FriendProfileResponse) {
        genericCacheDao.put(GenericCacheEntity(friendProfileCacheKey(userId, friendId), cacheGson.toJson(profile)))
    }

    override suspend fun updateCachedFriendshipStatus(userId: String, friendId: String, status: String) {
        val key = friendProfileCacheKey(userId, friendId)
        val existing = genericCacheDao.get(key)?.jsonPayload
            ?.let { json -> runCatching { cacheGson.fromJson(json, FriendProfileResponse::class.java) }.getOrNull() }
            ?: return
        genericCacheDao.put(GenericCacheEntity(key, cacheGson.toJson(existing.copy(friendshipStatus = status))))
    }

    override suspend fun getTasteProfile(userId: String): Result<List<GenreAffinity>> = safeApiCall {
        val response = userApiService.getTasteProfile(userId)
        if (response.isSuccessful) {
            val genres = response.body()?.genres ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(tasteCacheKey(userId), cacheGson.toJson(genres)))
            genres
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>> = safeApiCall {
        val response = userApiService.getFriendActivity(userId)
        if (response.isSuccessful) {
            val activities = response.body()?.activities ?: emptyList()
            genericCacheDao.put(GenericCacheEntity(activityCacheKey(userId), cacheGson.toJson(activities)))
            activities
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getMovieNightSuggestions(userId: String, friendId: String, page: Int, forceRefresh: Boolean): Result<MovieNightResponse> {
        // The page is part of the key: every page is cached separately.
        val cacheKey = "$userId:$friendId:$page"
        if (!forceRefresh) movieNightCache[cacheKey]?.let { return Result.success(it) }

        // Second cache level in Room (survives process restarts); a hit is also put back into
        // memory so later calls in the same session skip the Room read.
        if (!forceRefresh) genericCacheDao.get(movieNightCacheKey(userId, friendId, page))?.jsonPayload?.let { json ->
            val cached = runCatching {
                cacheGson.fromJson(json, MovieNightResponse::class.java)
            }.getOrNull()
            if (cached != null) {
                movieNightCache[cacheKey] = cached
                return Result.success(cached)
            }
        }

        return safeApiCall {
            val response = userApiService.getMovieNightSuggestions(userId, friendId, page)
            if (response.isSuccessful) {
                val body = response.body() ?: MovieNightResponse()
                movieNightCache[cacheKey] = body
                genericCacheDao.put(GenericCacheEntity(movieNightCacheKey(userId, friendId, page), cacheGson.toJson(body)))
                body
            } else {
                throw HttpException(response)
            }
        }
    }

    override suspend fun prefetchMovieNightSuggestions(userId: String, friendId: String) {
        val cacheKey = "$userId:$friendId:1"
        if (movieNightCache.containsKey(cacheKey)) return
        getMovieNightSuggestions(userId, friendId, page = 1)
    }
}
