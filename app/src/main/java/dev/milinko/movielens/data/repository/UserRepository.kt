package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.remote.user.AddToHistoryBody
import dev.milinko.movielens.data.remote.user.UpdateHistoryBody
import dev.milinko.movielens.data.remote.user.UserApiService
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSettings
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.util.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun getUserProfile(userId: String): Result<UserProfile>
    suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile>

    suspend fun getUserSettings(userId: String): Result<UserSettings>
    suspend fun updateUserSettings(userId: String, settings: UserSettings): Result<UserSettings>

    suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>>
    suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem>
    suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit>

    suspend fun getHistory(userId: String): Result<List<HistoryItem>>
    // rating/duration su opcioni - "označeno kao odgledano bez ocene" šalje oba kao null (ne kao
    // 0.0, koji bi backend zaokružio na ocenu 1 - videti Double?.toRatingIntOrNull() na backendu).
    suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double? = null, duration: Int? = null): Result<HistoryItem>
    suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double): Result<HistoryItem>
    // "Undo" za slučajno "označi kao odgledano" - briše i ocenu (isti red na backendu).
    suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit>

    suspend fun getFriends(userId: String): Result<List<FriendInfo>>
    suspend fun addFriend(userId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(userId: String, friendId: String): Result<Unit>

    suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>>

    suspend fun getMovieNightSuggestions(userId: String, friendId: String): Result<List<MovieSuggestion>>
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService
) : UserRepository {

    override suspend fun getUserProfile(userId: String): Result<UserProfile> = safeApiCall {
        val response = userApiService.getUserProfile(userId)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Get profile failed: ${response.code()}")
        }
    }

    override suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile> = safeApiCall {
        val response = userApiService.updateUserProfile(userId, profile)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Update profile failed: ${response.code()}")
        }
    }

    override suspend fun getUserSettings(userId: String): Result<UserSettings> = safeApiCall {
        val response = userApiService.getUserSettings(userId)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Get settings failed: ${response.code()}")
        }
    }

    override suspend fun updateUserSettings(userId: String, settings: UserSettings): Result<UserSettings> = safeApiCall {
        val response = userApiService.updateUserSettings(userId, settings)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Update settings failed: ${response.code()}")
        }
    }

    override suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>> = safeApiCall {
        val response = userApiService.getWatchlist(userId)
        if (response.isSuccessful) {
            response.body()?.items ?: emptyList()
        } else {
            throw Exception("Get watchlist failed: ${response.code()}")
        }
    }

    override suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem> = safeApiCall {
        val response = userApiService.addToWatchlist(userId, mapOf("tmdbId" to tmdbId))
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Add to watchlist failed: ${response.code()}")
        }
    }

    override suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromWatchlist(userId, tmdbId)
        if (!response.isSuccessful) {
            throw Exception("Remove from watchlist failed: ${response.code()}")
        }
    }

    override suspend fun getHistory(userId: String): Result<List<HistoryItem>> = safeApiCall {
        val response = userApiService.getHistory(userId)
        if (response.isSuccessful) {
            response.body()?.items ?: emptyList()
        } else {
            throw Exception("Get history failed: ${response.code()}")
        }
    }

    override suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double?, duration: Int?): Result<HistoryItem> = safeApiCall {
        // rating/duration null se serijalizuju kao JSON null (Gson) - backend AddToHistoryRequest
        // ima podrazumevanu vrednost null za oba, pa je to ekvivalentno izostavljanju polja.
        val response = userApiService.addToHistory(userId, AddToHistoryBody(tmdbId, rating, duration))
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Add to history failed: ${response.code()}")
        }
    }

    override suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double): Result<HistoryItem> = safeApiCall {
        val response = userApiService.updateHistoryItem(userId, tmdbId, UpdateHistoryBody(rating))
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Update history failed: ${response.code()}")
        }
    }

    override suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromHistory(userId, tmdbId)
        if (!response.isSuccessful) {
            throw Exception("Remove from history failed: ${response.code()}")
        }
    }

    override suspend fun getFriends(userId: String): Result<List<FriendInfo>> = safeApiCall {
        val response = userApiService.getFriends(userId)
        if (response.isSuccessful) {
            response.body()?.friends ?: emptyList()
        } else {
            throw Exception("Get friends failed: ${response.code()}")
        }
    }

    override suspend fun addFriend(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, mapOf("action" to "add"))
        if (!response.isSuccessful) {
            throw Exception("Add friend failed: ${response.code()}")
        }
    }

    override suspend fun removeFriend(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, mapOf("action" to "remove"))
        if (!response.isSuccessful) {
            throw Exception("Remove friend failed: ${response.code()}")
        }
    }

    override suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>> = safeApiCall {
        val response = userApiService.getFriendActivity(userId)
        if (response.isSuccessful) {
            response.body()?.activities ?: emptyList()
        } else {
            throw Exception("Get activity failed: ${response.code()}")
        }
    }

    override suspend fun getMovieNightSuggestions(userId: String, friendId: String): Result<List<MovieSuggestion>> = safeApiCall {
        val response = userApiService.getMovieNightSuggestions(userId, friendId)
        if (response.isSuccessful) {
            response.body()?.suggestions ?: emptyList()
        } else {
            throw Exception("Get suggestions failed: ${response.code()}")
        }
    }
}
