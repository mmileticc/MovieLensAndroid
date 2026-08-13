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
import retrofit2.http.Query
import retrofit2.http.Body

// Tela zahteva za POST/PUT rute ispod - namerno pravi tipovi umesto Map<String, Any>.
// Kotlin-ov Map<K, out V> je kovarijantan u V; kad je V == Any, kompajlirani JVM potpis dobija
// "? extends Object" wildcard u generic signature-u, a Retrofit-ova (lenjy, tek pri prvom pozivu
// te metode) validacija to eksplicitno odbija sa "Parameter type must not include a type variable
// or wildcard". Ovo se dešavalo baš na addToHistory/updateHistoryItem (Map<String, Any>) - i
// Map<String, Int>/Map<String, String> na drugim rutama slučajno rade jer generic signature za te
// value tipove ne dobija problematičan wildcard. Umesto da se oslanjamo na tu slučajnost, sve nove
// body tipove pišemo kao prave data klase.
data class AddToHistoryBody(val tmdbId: Int, val rating: Double? = null, val duration: Int? = null)
data class UpdateHistoryBody(val rating: Double)

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
        @Body item: AddToHistoryBody
    ): Response<HistoryItem>

    @PUT("api/users/{userId}/history/{tmdbId}")
    suspend fun updateHistoryItem(
        @Path("userId") userId: String,
        @Path("tmdbId") tmdbId: Int,
        @Body item: UpdateHistoryBody
    ): Response<HistoryItem>

    // "Undo" za "označi kao odgledano" (lapsus klik) - briše i ocenu, ista tabela/red na backendu.
    @DELETE("api/users/{userId}/history/{tmdbId}")
    suspend fun removeFromHistory(
        @Path("userId") userId: String,
        @Path("tmdbId") tmdbId: Int
    ): Response<Unit>

    // Pretraga korisnika (search-as-you-type za dodavanje prijatelja) - flat ruta, "ko sam ja" se
    // čita iz JWT-a na backendu, ne šalje se kao path parametar.
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

    // Profil prijatelja (ili sopstveni) - dozvoljeno samo za sebe ili ACCEPTED prijatelja,
    // backend vraća 403 inače.
    @GET("api/users/{userId}/friends/{friendId}/profile")
    suspend fun getFriendProfile(
        @Path("userId") userId: String,
        @Path("friendId") friendId: String
    ): Response<FriendProfileResponse>

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

