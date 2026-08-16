package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.local.HistoryDao
import dev.milinko.movielens.data.local.HistoryEntity
import dev.milinko.movielens.data.local.WatchlistDao
import dev.milinko.movielens.data.local.WatchlistEntity
import dev.milinko.movielens.data.remote.user.AddToHistoryBody
import dev.milinko.movielens.data.remote.user.FriendActionBody
import dev.milinko.movielens.data.remote.user.FriendProfileResponse
import dev.milinko.movielens.data.remote.user.GenreAffinity
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UpdateHistoryBody
import dev.milinko.movielens.data.remote.user.UserApiService
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.data.remote.user.UserSettings
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.util.safeApiCall
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

interface UserRepository {
    suspend fun getUserProfile(userId: String): Result<UserProfile>
    suspend fun updateUserProfile(userId: String, profile: UserProfile): Result<UserProfile>

    suspend fun getUserSettings(userId: String): Result<UserSettings>
    suspend fun updateUserSettings(userId: String, settings: UserSettings): Result<UserSettings>

    // Offline-first: čita se odmah iz Room keša (instant, radi i bez interneta), getWatchlist
    // ispod ostaje "osveži sa mreže" - poziva se u pozadini, upisuje u isti keš, Flow automatski
    // emituje novu vrednost UI-ju. Videti diskusiju u MOVIELENS_ROADMAP.md o offline obimu -
    // namerno SAMO watchlist/istorija, ne i pretraga/trending/bioskopski termini.
    fun observeWatchlist(userId: String): Flow<List<WatchlistItem>>
    suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>>
    suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem>
    suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit>

    fun observeHistory(userId: String): Flow<List<HistoryItem>>
    suspend fun getHistory(userId: String): Result<List<HistoryItem>>
    // rating/duration su opcioni - "označeno kao odgledano bez ocene" šalje oba kao null (ne kao
    // 0.0, koji bi backend zaokružio na ocenu 1 - videti Double?.toRatingIntOrNull() na backendu).
    // review - kratak tekstualni komentar uz ocenu (opcion, nezavisan od rating-a).
    suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double? = null, review: String? = null, duration: Int? = null): Result<HistoryItem>
    // rating/review = null znače "ne diraj postojeću vrednost" (npr. izmena samo komentara bez
    // dodira na ocenu, ili obrnuto) - videti napomenu u backend UserActionsRepository.markWatched.
    suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double?, review: String? = null): Result<HistoryItem>
    // "Undo" za slučajno "označi kao odgledano" - briše i ocenu (isti red na backendu).
    suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit>

    // Pretraga korisnika za dodavanje prijatelja (search-as-you-type, kao Search filmova).
    suspend fun searchUsers(query: String): Result<List<UserSearchResult>>
    suspend fun getPendingFriendRequests(userId: String): Result<List<PendingRequest>>

    suspend fun getFriends(userId: String): Result<List<FriendInfo>>
    suspend fun addFriend(userId: String, friendId: String): Result<Unit>
    suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit>
    suspend fun removeFriend(userId: String, friendId: String): Result<Unit>
    suspend fun getFriendProfile(userId: String, friendId: String): Result<FriendProfileResponse>
    suspend fun getTasteProfile(userId: String): Result<List<GenreAffinity>>

    suspend fun getFriendActivity(userId: String): Result<List<FriendActivity>>

    suspend fun getMovieNightSuggestions(userId: String, friendId: String, page: Int = 1): Result<List<MovieSuggestion>>

    /**
     * Učitava movienight-suggestions unapred (npr. čim se otvori profil prijatelja) i drži ih u
     * memoriji da getMovieNightSuggestions posle vrati odmah bez čekanja mreže kad korisnik
     * stvarno klikne na dugme. Tiho gutа grešku - ovo je samo optimizacija, ne kritičan poziv;
     * ako ne uspe, obično dugme i dalje radi normalno (samo bez prefetch-a).
     */
    suspend fun prefetchMovieNightSuggestions(userId: String, friendId: String)
}

private data class ApiErrorBody(val error: String? = null)

/** Parsira backend-ovo `{"error": "..."}` telo greške (ErrorResponse.kt) u čitljivu poruku. */
private fun Response<*>.parseErrorMessage(fallback: String): String {
    return try {
        val raw = errorBody()?.string()
        if (raw.isNullOrBlank()) fallback else Gson().fromJson(raw, ApiErrorBody::class.java)?.error ?: fallback
    } catch (e: Exception) {
        fallback
    }
}

private fun WatchlistEntity.toDomain() = WatchlistItem(
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    addedAt = addedAt,
    priority = priority
)

private fun WatchlistItem.toEntity(userId: String) = WatchlistEntity(
    userId = userId,
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    addedAt = addedAt,
    priority = priority
)

private fun HistoryEntity.toDomain() = HistoryItem(
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    userRating = userRating,
    review = review,
    watchedAt = watchedAt,
    duration = duration
)

private fun HistoryItem.toEntity(userId: String) = HistoryEntity(
    userId = userId,
    tmdbId = tmdbId,
    title = title,
    posterPath = posterPath,
    userRating = userRating,
    review = review,
    watchedAt = watchedAt,
    duration = duration
)

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApiService: UserApiService,
    private val watchlistDao: WatchlistDao,
    private val historyDao: HistoryDao
) : UserRepository {

    // Memorijski keš za movienight predloge (userId:friendId -> lista) - popunjava se preko
    // prefetchMovieNightSuggestions kad se otvori profil prijatelja, čita se u
    // getMovieNightSuggestions da MovieNightScreen ne mora da čeka mrežu ako je već prefetch-ovano.
    // ConcurrentHashMap jer prefetch i stvaran poziv mogu teoretski da se preklope.
    private val movieNightCache = java.util.concurrent.ConcurrentHashMap<String, List<MovieSuggestion>>()

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
            // Backend vraća konkretnu poruku (npr. "Korisničko ime je već zauzeto") u JSON telu
            // greške ({"error": "..."}) - izvlačimo je da UI može da prikaže nešto korisnije od
            // golog HTTP koda (jedini poziv u repo-u gde je poruka greške stvarno actionable za
            // korisnika, za razliku od ostalih mrežnih poziva ovde).
            throw Exception(response.parseErrorMessage("Ažuriranje profila nije uspelo (${response.code()})"))
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

    override fun observeWatchlist(userId: String): Flow<List<WatchlistItem>> =
        watchlistDao.observeWatchlist(userId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getWatchlist(userId: String): Result<List<WatchlistItem>> = safeApiCall {
        val response = userApiService.getWatchlist(userId)
        if (response.isSuccessful) {
            val items = response.body()?.items ?: emptyList()
            // Osveži lokalni keš sa serverom - Flow iz observeWatchlist automatski emituje ovu
            // novu vrednost svakom kolektoru (Room je single source of truth za UI).
            watchlistDao.replaceAll(userId, items.map { it.toEntity(userId) })
            items
        } else {
            throw Exception("Get watchlist failed: ${response.code()}")
        }
    }

    override suspend fun addToWatchlist(userId: String, tmdbId: Int): Result<WatchlistItem> = safeApiCall {
        val response = userApiService.addToWatchlist(userId, mapOf("tmdbId" to tmdbId))
        if (response.isSuccessful) {
            val item = response.body() ?: throw Exception("Empty response")
            watchlistDao.insert(item.toEntity(userId))
            item
        } else {
            throw Exception("Add to watchlist failed: ${response.code()}")
        }
    }

    override suspend fun removeFromWatchlist(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromWatchlist(userId, tmdbId)
        if (response.isSuccessful) {
            watchlistDao.delete(userId, tmdbId)
        } else {
            throw Exception("Remove from watchlist failed: ${response.code()}")
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
            throw Exception("Get history failed: ${response.code()}")
        }
    }

    override suspend fun addToHistory(userId: String, tmdbId: Int, rating: Double?, review: String?, duration: Int?): Result<HistoryItem> = safeApiCall {
        // rating/duration null se serijalizuju kao JSON null (Gson) - backend AddToHistoryRequest
        // ima podrazumevanu vrednost null za oba, pa je to ekvivalentno izostavljanju polja.
        val response = userApiService.addToHistory(userId, AddToHistoryBody(tmdbId, rating, review, duration))
        if (response.isSuccessful) {
            val item = response.body() ?: throw Exception("Empty response")
            historyDao.insert(item.toEntity(userId))
            item
        } else {
            throw Exception("Add to history failed: ${response.code()}")
        }
    }

    override suspend fun updateHistoryItem(userId: String, tmdbId: Int, rating: Double?, review: String?): Result<HistoryItem> = safeApiCall {
        val response = userApiService.updateHistoryItem(userId, tmdbId, UpdateHistoryBody(rating, review))
        if (response.isSuccessful) {
            val item = response.body() ?: throw Exception("Empty response")
            historyDao.insert(item.toEntity(userId))
            item
        } else {
            throw Exception("Update history failed: ${response.code()}")
        }
    }

    override suspend fun removeFromHistory(userId: String, tmdbId: Int): Result<Unit> = safeApiCall {
        val response = userApiService.removeFromHistory(userId, tmdbId)
        if (response.isSuccessful) {
            historyDao.delete(userId, tmdbId)
        } else {
            throw Exception("Remove from history failed: ${response.code()}")
        }
    }

    override suspend fun searchUsers(query: String): Result<List<UserSearchResult>> = safeApiCall {
        val response = userApiService.searchUsers(query)
        if (response.isSuccessful) {
            response.body()?.users ?: emptyList()
        } else {
            throw Exception("Search users failed: ${response.code()}")
        }
    }

    override suspend fun getPendingFriendRequests(userId: String): Result<List<PendingRequest>> = safeApiCall {
        val response = userApiService.getPendingFriendRequests(userId)
        if (response.isSuccessful) {
            response.body()?.requests ?: emptyList()
        } else {
            throw Exception("Get pending requests failed: ${response.code()}")
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
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("add"))
        if (!response.isSuccessful) {
            throw Exception("Add friend failed: ${response.code()}")
        }
    }

    override suspend fun acceptFriendRequest(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("accept"))
        if (!response.isSuccessful) {
            throw Exception("Accept friend request failed: ${response.code()}")
        }
    }

    override suspend fun rejectFriendRequest(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.manageFriend(userId, friendId, FriendActionBody("reject"))
        if (!response.isSuccessful) {
            throw Exception("Reject friend request failed: ${response.code()}")
        }
    }

    override suspend fun removeFriend(userId: String, friendId: String): Result<Unit> = safeApiCall {
        val response = userApiService.removeFriend(userId, friendId)
        if (!response.isSuccessful) {
            throw Exception("Remove friend failed: ${response.code()}")
        }
    }

    override suspend fun getFriendProfile(userId: String, friendId: String): Result<FriendProfileResponse> = safeApiCall {
        val response = userApiService.getFriendProfile(userId, friendId)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Get friend profile failed: ${response.code()}")
        }
    }

    override suspend fun getTasteProfile(userId: String): Result<List<GenreAffinity>> = safeApiCall {
        val response = userApiService.getTasteProfile(userId)
        if (response.isSuccessful) {
            response.body()?.genres ?: emptyList()
        } else {
            throw Exception("Get taste profile failed: ${response.code()}")
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

    override suspend fun getMovieNightSuggestions(userId: String, friendId: String, page: Int): Result<List<MovieSuggestion>> {
        // page je deo ključa - "Osveži predloge" traži sledeću stranu pod drugim ključem, pa
        // prirodno zaobilazi keš (stvaran mrežni poziv) bez posebnog "forceRefresh" parametra.
        val cacheKey = "$userId:$friendId:$page"
        movieNightCache[cacheKey]?.let { return Result.success(it) }

        return safeApiCall {
            val response = userApiService.getMovieNightSuggestions(userId, friendId, page)
            if (response.isSuccessful) {
                val suggestions = response.body()?.suggestions ?: emptyList()
                movieNightCache[cacheKey] = suggestions
                suggestions
            } else {
                throw Exception("Get suggestions failed: ${response.code()}")
            }
        }
    }

    override suspend fun prefetchMovieNightSuggestions(userId: String, friendId: String) {
        val cacheKey = "$userId:$friendId:1"
        if (movieNightCache.containsKey(cacheKey)) return
        getMovieNightSuggestions(userId, friendId, page = 1)
    }
}
