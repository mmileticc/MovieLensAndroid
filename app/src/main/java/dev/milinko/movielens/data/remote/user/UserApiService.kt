package dev.milinko.movielens.data.remote.user

import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.HistoryResponse
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.WatchlistResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body

// Request bodies are real data classes, never Map<String, Any>: Kotlin's Map<K, out V> with V = Any
// compiles to a "? extends Object" wildcard in the JVM signature, which Retrofit rejects at the
// first call ("Parameter type must not include a type variable or wildcard").
data class AddToHistoryBody(val tmdbId: Int, val rating: Double? = null, val review: String? = null, val duration: Int? = null)
// rating/review = null mean "keep the existing value" (e.g. change only the rating). See the
// backend's UserActionsRepository.markWatched.
data class UpdateHistoryBody(val rating: Double? = null, val review: String? = null)

/** The signed-in user's data and the social features (`/api/users/{userId}/...`); all require sign-in. */
interface UserApiService {
    // Profile
    @GET("api/users/{userId}")
    suspend fun getUserProfile(
        @Path("userId") userId: String
    ): Response<UserProfile>

    @PUT("api/users/{userId}/profile")
    suspend fun updateUserProfile(
        @Path("userId") userId: String,
        @Body profile: UserProfile
    ): Response<UserProfile>

    // Watchlist
    @GET("api/users/{userId}/watchlist")
    suspend fun getWatchlist(
        @Path("userId") userId: String
    ): Response<WatchlistResponse>

    @POST("api/users/{userId}/watchlist")
    suspend fun addToWatchlist(
        @Path("userId") userId: String,
        @Body item: Map<String, Int>
    ): Response<WatchlistItem>

    @DELETE("api/users/{userId}/watchlist/{tmdbId}")
    suspend fun removeFromWatchlist(
        @Path("userId") userId: String,
        @Path("tmdbId") tmdbId: Int
    ): Response<Unit>

    // History (watched/rated)
    @GET("api/users/{userId}/history")
    suspend fun getHistory(
        @Path("userId") userId: String
    ): Response<HistoryResponse>

    @POST("api/users/{userId}/history")
    suspend fun addToHistory(
        @Path("userId") userId: String,
        @Body item: AddToHistoryBody
    ): Response<HistoryItem>

    @PUT("api/users/{userId}/history/{tmdbId}")
    suspend fun updateHistoryItem(
        @Path("userId") userId: String,
        @Path("tmdbId") tmdbId: Int,
        @Body item: UpdateHistoryBody
    ): Response<HistoryItem>

    // Undo for "mark as watched" - also removes the rating (same row on the backend).
    @DELETE("api/users/{userId}/history/{tmdbId}")
    suspend fun removeFromHistory(
        @Path("userId") userId: String,
        @Path("tmdbId") tmdbId: Int
    ): Response<Unit>

    // User search for adding friends (search as you type). The caller comes from the JWT.
    @GET("api/users/search")
    suspend fun searchUsers(
        @Query("q") query: String
    ): Response<UserSearchResponse>

    // Friends
    @GET("api/users/{userId}/friends")
    suspend fun getFriends(
        @Path("userId") userId: String
    ): Response<FriendsResponse>

    @GET("api/users/{userId}/friends/pending")
    suspend fun getPendingFriendRequests(
        @Path("userId") userId: String
    ): Response<PendingRequestsResponse>

    // Outgoing (sent) friend requests.
    @GET("api/users/{userId}/friends/pending/sent")
    suspend fun getSentFriendRequests(
        @Path("userId") userId: String
    ): Response<PendingRequestsResponse>

    @POST("api/users/{userId}/friends/{friendId}")
    suspend fun manageFriend(
        @Path("userId") userId: String,
        @Path("friendId") friendId: String,
        @Body body: FriendActionBody
    ): Response<Unit>

    @DELETE("api/users/{userId}/friends/{friendId}")
    suspend fun removeFriend(
        @Path("userId") userId: String,
        @Path("friendId") friendId: String
    ): Response<Unit>

    // A user's profile: full for oneself or an accepted friend, a limited preview otherwise.
    @GET("api/users/{userId}/friends/{friendId}/profile")
    suspend fun getFriendProfile(
        @Path("userId") userId: String,
        @Path("friendId") friendId: String
    ): Response<FriendProfileResponse>

    // Taste profile (average rating per genre), own profile only.
    @GET("api/users/{userId}/taste-profile")
    suspend fun getTasteProfile(
        @Path("userId") userId: String
    ): Response<TasteProfileResponse>

    // Friend activity feed
    @GET("api/users/{userId}/friends/activity")
    suspend fun getFriendActivity(
        @Path("userId") userId: String
    ): Response<FriendActivityResponse>

    // Movie Night suggestions. `page` supports the "Refresh suggestions" button.
    @GET("api/users/{userId}/friends/{friendId}/movienight-suggestions")
    suspend fun getMovieNightSuggestions(
        @Path("userId") userId: String,
        @Path("friendId") friendId: String,
        @Query("page") page: Int = 1
    ): Response<MovieNightResponse>

    // Push notifications: registers this device for the signed-in account.
    @PUT("api/users/{userId}/device-token")
    suspend fun registerDeviceToken(
        @Path("userId") userId: String,
        @Body body: DeviceTokenBody
    ): Response<Unit>

    // On sign-out the session is already cleared, so the account's token is passed explicitly.
    @DELETE("api/users/{userId}/device-token")
    suspend fun unregisterDeviceToken(
        @Path("userId") userId: String,
        @Query("token") token: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>

    // Sends a movie to a friend (delivered as a push notification).
    @POST("api/users/{userId}/share")
    suspend fun shareMovie(
        @Path("userId") userId: String,
        @Body body: ShareMovieBody
    ): Response<Unit>

    @GET("api/users/{userId}/notifications")
    suspend fun getNotifications(
        @Path("userId") userId: String
    ): Response<NotificationsResponse>

    @POST("api/users/{userId}/notifications/read")
    suspend fun markNotificationsRead(
        @Path("userId") userId: String
    ): Response<Unit>
}
