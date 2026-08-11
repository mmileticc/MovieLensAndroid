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

    override suspend fun getUserProfile(userId: String): Result<UserProfile> {
        return try {
            val response = userApiService.getUserProfile(userId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Get profile failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile> {
        return try {
            val response = userApiService.updateUserProfile(userId, profile)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Update profile failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserSettings(userId: String): Result<UserSettings> {
        return try {
            val response = userApiService.getUserSettings(userId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Get settings failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateUserSettings(userId: String, settings: UserSettings): Result<UserSettings> {
        return try {
            val response = userApiService.updateUserSettings(userId, settings)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Update settings failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>> {
        return try {
            val response = userApiService.getWatchlist(userId)
            if (response.isSuccessful) {
                Result.success(response.body()?.items ?: emptyList())
            } else {
                Result.failure(Exception("Get watchlist failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem> {
        return try {
            val response = userApiService.addToWatchlist(userId, mapOf("tmdbId" to tmdbId))
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Add to watchlist failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit> {
        return try {
            val response = userApiService.removeFromWatchlist(userId, tmdbId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Remove from watchlist failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getHistory(userId: String): Result<List<HistoryItem>> {
        return try {
            val response = userApiService.getHistory(userId)
            if (response.isSuccessful) {
                Result.success(response.body()?.items ?: emptyList())
            } else {
                Result.failure(Exception("Get history failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double?, duration: Int?): Result<HistoryItem> {
        return try {
            // rating/duration null se serijalizuju kao JSON null (Gson) - backend AddToHistoryRequest
            // ima podrazumevanu vrednost null za oba, pa je to ekvivalentno izostavljanju polja.
            val response = userApiService.addToHistory(userId, AddToHistoryBody(tmdbId, rating, duration))
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Add to history failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double): Result<HistoryItem> {
        return try {
            val response = userApiService.updateHistoryItem(userId, tmdbId, UpdateHistoryBody(rating))
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Update history failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit> {
        return try {
            val response = userApiService.removeFromHistory(userId, tmdbId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Remove from history failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFriends(userId: String): Result<List<FriendInfo>> {
        return try {
            val response = userApiService.getFriends(userId)
            if (response.isSuccessful) {
                Result.success(response.body()?.friends ?: emptyList())
            } else {
                Result.failure(Exception("Get friends failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addFriend(userId: String, friendId: String): Result<Unit> {
        return try {
            val response = userApiService.manageFriend(userId, friendId, mapOf("action" to "add"))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Add friend failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeFriend(userId: String, friendId: String): Result<Unit> {
        return try {
            val response = userApiService.manageFriend(userId, friendId, mapOf("action" to "remove"))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Remove friend failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>> {
        return try {
            val response = userApiService.getFriendActivity(userId)
            if (response.isSuccessful) {
                Result.success(response.body()?.activities ?: emptyList())
            } else {
                Result.failure(Exception("Get activity failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMovieNightSuggestions(userId: String, friendId: String): Result<List<MovieSuggestion>> {
        return try {
            val response = userApiService.getMovieNightSuggestions(userId, friendId)
            if (response.isSuccessful) {
                Result.success(response.body()?.suggestions ?: emptyList())
            } else {
                Result.failure(Exception("Get suggestions failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

