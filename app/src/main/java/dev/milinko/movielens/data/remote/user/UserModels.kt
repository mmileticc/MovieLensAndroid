package dev.milinko.movielens.data.remote.user

import com.google.gson.annotations.SerializedName

// User Profile
data class UserProfile(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("favorite_genres")
    val favoriteGenres: List<String> = emptyList(),
    @SerializedName("stats")
    val stats: UserStats = UserStats()
)

data class UserStats(
    @SerializedName("movies_watched")
    val moviesWatched: Int = 0,
    @SerializedName("movies_rated")
    val moviesRated: Int = 0,
    @SerializedName("friend_count")
    val friendCount: Int = 0
)

// User Settings
data class UserSettings(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("dark_mode")
    val darkMode: Boolean = true,
    @SerializedName("notifications")
    val notifications: Boolean = true,
    @SerializedName("data_saver_mode")
    val dataSaverMode: Boolean = false,
    @SerializedName("language")
    val language: String = "sr"
)

// Friends
data class FriendInfo(
    @SerializedName("friend_id")
    val friendId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("avatar")
    val avatar: String? = null,
    @SerializedName("match_score")
    val matchScore: Float = 0f, // 0-100%
    @SerializedName("common_genres")
    val commonGenres: List<String> = emptyList(),
    @SerializedName("mutual_watchlist")
    val mutualWatchlist: List<MutualMovie> = emptyList()
)

data class MutualMovie(
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String
)

data class FriendsResponse(
    @SerializedName("friends")
    val friends: List<FriendInfo> = emptyList()
)

// Pretraga korisnika za dodavanje prijatelja (search-as-you-type)
data class UserSearchResult(
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    // NONE / PENDING_SENT / PENDING_RECEIVED / ACCEPTED - videti FriendshipStatus na backendu
    @SerializedName("status")
    val status: String = "NONE"
)

data class UserSearchResponse(
    @SerializedName("users")
    val users: List<UserSearchResult> = emptyList()
)

// Dolazni (nerešeni) zahtevi za prijateljstvo
data class PendingRequest(
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null
)

data class PendingRequestsResponse(
    @SerializedName("requests")
    val requests: List<PendingRequest> = emptyList()
)

// Telo POST /friends/{friendId} - "add" | "accept" | "reject". Pravi tip umesto Map<String, Any> -
// videti napomenu u UserApiService.kt o Kotlin Map<K, out V> + Retrofit wildcard bug-u.
data class FriendActionBody(val action: String)

// Profil prijatelja - osnovni info + WATCHED lista (watchlist ostaje privatan, nikad se ne vraća).
data class FriendProfileMovie(
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("rating")
    val rating: Int? = null,
    @SerializedName("watched_at")
    val watchedAt: Long = 0L
)

data class FriendProfileResponse(
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("watched_movies")
    val watchedMovies: List<FriendProfileMovie> = emptyList()
)

// Taste profile (Faza 4) - prosečna ocena po žanru, izvedeno direktno iz korisnikovih ocena.
data class GenreAffinity(
    @SerializedName("genre_id")
    val genreId: Int,
    @SerializedName("genre")
    val genre: String,
    @SerializedName("average_rating")
    val averageRating: Double,
    @SerializedName("movie_count")
    val movieCount: Int
)

data class TasteProfileResponse(
    @SerializedName("genres")
    val genres: List<GenreAffinity> = emptyList()
)

// "Prijatelji su gledali ovaj film" bedž na Movie Detail-u
data class FriendWatched(
    @SerializedName("friend_id")
    val friendId: String,
    @SerializedName("friend_name")
    val friendName: String,
    @SerializedName("rating")
    val rating: Int? = null
)

data class FriendsWatchedResponse(
    @SerializedName("friends")
    val friends: List<FriendWatched> = emptyList()
)

// Friend Activity
data class FriendActivity(
    @SerializedName("activity_id")
    val activityId: String,
    @SerializedName("friend_id")
    val friendId: String,
    @SerializedName("friend_name")
    val friendName: String,
    @SerializedName("action")
    val action: String, // "rated", "watched", "added_to_watchlist"
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("movie_title")
    val movieTitle: String,
    @SerializedName("metadata")
    val metadata: ActivityMetadata = ActivityMetadata(),
    @SerializedName("timestamp")
    val timestamp: Long
)

data class ActivityMetadata(
    @SerializedName("rating")
    val rating: Double? = null,
    @SerializedName("watched_at")
    val watchedAt: Long? = null
)

data class FriendActivityResponse(
    @SerializedName("activities")
    val activities: List<FriendActivity> = emptyList()
)

// MovieNight Suggestions
data class MovieSuggestion(
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("match_reason")
    val matchReason: String, // "Both rated 8+", "Common genre", etc.
    @SerializedName("scores")
    val scores: SuggestionScores = SuggestionScores()
)

data class SuggestionScores(
    @SerializedName("your_match")
    val yourMatch: Float = 0f,
    @SerializedName("their_match")
    val theirMatch: Float = 0f,
    @SerializedName("combined_score")
    val combinedScore: Float = 0f
)

data class MovieNightResponse(
    @SerializedName("suggestions")
    val suggestions: List<MovieSuggestion> = emptyList()
)

