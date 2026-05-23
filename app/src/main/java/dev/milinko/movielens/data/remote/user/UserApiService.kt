package dev.milinko.movielens.data.remote.user

import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.HistoryResponse
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.WatchlistResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Body

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

    // Settings
    @GET("api/users/{userId}/settings")
    suspend fun getUserSettings(
        @Path("userId") userId: String
    ): Response<UserSettings>

    @PUT("api/users/{userId}/settings")
    suspend fun updateUserSettings(
        @Path("userId") userId: String,
        @Body settings: UserSettings
    ): Response<UserSettings>

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

    // History
    @GET("api/users/{userId}/history")
    suspend fun getHistory(
        @Path("userId") userId: String
    ): Response<HistoryResponse>

    @POST("api/users/{userId}/history")
    suspend fun addToHistory(
        @Path("userId") userId: String,
        @Body item: Map<String, Any>
    ): Response<HistoryItem>

    @PUT("api/users/{userId}/history/{tmdbId}")
    suspend fun updateHistoryItem(
        @Path("userId") userId: String,
        @Path("tmdbId") tmdbId: Int,
        @Body item: Map<String, Any>
    ): Response<HistoryItem>

    // Friends
    @GET("api/users/{userId}/friends")
    suspend fun getFriends(
        @Path("userId") userId: String
    ): Response<FriendsResponse>

    @POST("api/users/{userId}/friends/{friendId}")
    suspend fun manageFriend(
        @Path("userId") userId: String,
        @Path("friendId") friendId: String,
        @Body action: Map<String, String>
    ): Response<Unit>

    // Friend Activity
    @GET("api/users/{userId}/friends/activity")
    suspend fun getFriendActivity(
        @Path("userId") userId: String
    ): Response<FriendActivityResponse>

    // MovieNight Suggestions
    @GET("api/users/{userId}/friends/{friendId}/movienight-suggestions")
    suspend fun getMovieNightSuggestions(
        @Path("userId") userId: String,
        @Path("friendId") friendId: String
    ): Response<MovieNightResponse>
}

