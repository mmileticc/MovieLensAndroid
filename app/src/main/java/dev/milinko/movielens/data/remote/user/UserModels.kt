package dev.milinko.movielens.data.remote.user

import com.google.gson.annotations.SerializedName

/** The signed-in user's profile with statistics and top genres. */
data class UserProfile(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("email")
    val email: String,
    @SerializedName("avatar")
    val avatar: String? = null,
    // Short status line under the name, set by the user.
    @SerializedName("bio")
    val bio: String? = null,
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

/** A friend with the taste match and shared genres. */
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

/** A user search result for adding friends. */
data class UserSearchResult(
    @SerializedName("id")
    val id: String,
    @SerializedName("username")
    val username: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    // NONE / PENDING_SENT / PENDING_RECEIVED / ACCEPTED - see FriendshipStatus on the backend
    @SerializedName("status")
    val status: String = "NONE"
)

data class UserSearchResponse(
    @SerializedName("users")
    val users: List<UserSearchResult> = emptyList()
)

/** A pending friend request (incoming or sent). */
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

// Body of POST /friends/{friendId}: "add" | "accept" | "reject" (a real type, see UserApiService).
data class FriendActionBody(val action: String)

/** A movie from a friend's watched list (watchlists are never shown to others). */
data class FriendProfileMovie(
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("rating")
    val rating: Int? = null,
    @SerializedName("review")
    val review: String? = null,
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
    // The friend's status line, same field as UserProfile.bio.
    @SerializedName("bio")
    val bio: String? = null,
    @SerializedName("watched_movies")
    val watchedMovies: List<FriendProfileMovie> = emptyList(),
    // Before the friendship is accepted (friendshipStatus != "ACCEPTED") the backend returns a
    // limited preview: watchedMovies is empty and email is null; commonGenres is shown instead.
    @SerializedName("email")
    val email: String? = null,
    // Nullable on purpose: Gson fills fields via reflection without calling the constructor, so a
    // Kotlin default applies only when the object is created in code - not when the key is missing
    // in JSON (e.g. old Room cache entries). A non-null String that is actually null crashes a
    // `when` on it; readers use `?:` / `.orEmpty()`.
    @SerializedName("friendship_status")
    val friendshipStatus: String? = "ACCEPTED",
    @SerializedName("common_genres")
    val commonGenres: List<String>? = emptyList()
)

/** Average rating per genre, derived from the user's ratings. */
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

/** A friend who watched the movie ("friends watched" badge on the detail screen). */
data class FriendWatched(
    @SerializedName("friend_id")
    val friendId: String,
    @SerializedName("friend_name")
    val friendName: String,
    @SerializedName("rating")
    val rating: Int? = null,
    @SerializedName("review")
    val review: String? = null
)

data class FriendsWatchedResponse(
    @SerializedName("friends")
    val friends: List<FriendWatched> = emptyList()
)

/** One entry of the friend activity feed. */
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
    // Shown in the activity feed instead of a plain icon.
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("metadata")
    val metadata: ActivityMetadata = ActivityMetadata(),
    @SerializedName("timestamp")
    val timestamp: Long
)

data class ActivityMetadata(
    @SerializedName("rating")
    val rating: Double? = null,
    @SerializedName("review")
    val review: String? = null,
    @SerializedName("watched_at")
    val watchedAt: Long? = null
)

data class FriendActivityResponse(
    @SerializedName("activities")
    val activities: List<FriendActivity> = emptyList()
)

/** A Movie Night suggestion for the user and a friend. */
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
    val scores: SuggestionScores = SuggestionScores(),
    // Nullable for the same Gson reason as MovieSearchResult.genreIds (older backends do not send it).
    @SerializedName("release_date")
    val releaseDate: String? = null,
    @SerializedName("vote_average")
    val voteAverage: Double? = null,
    @SerializedName("genre_ids")
    val genreIds: List<Int>? = null
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
    val suggestions: List<MovieSuggestion> = emptyList(),
    // Whether the backend has more TMDB pages for this genre mix after the returned one
    // (see MovieNightViewModel.refreshSuggestions).
    @SerializedName("has_more")
    val hasMore: Boolean = false,
    // Movie Night header (the same for all suggestions) - nullable, see MovieSuggestion.
    @SerializedName("friend_username")
    val friendUsername: String? = null,
    @SerializedName("shared_genres")
    val sharedGenres: List<dev.milinko.movielens.data.remote.movie.Genre>? = null,
    @SerializedName("match_score")
    val matchScore: Float? = null
)

data class DeviceTokenBody(
    @SerializedName("token")
    val token: String
)

data class ShareMovieBody(
    @SerializedName("friendId")
    val friendId: String,
    @SerializedName("tmdbId")
    val tmdbId: Int,
    @SerializedName("note")
    val note: String? = null
)

/** One stored notification (GET /api/users/{id}/notifications); `data` holds the same values as the push message. */
data class NotificationItem(
    @SerializedName("id")
    val id: Int,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("content")
    val content: String = "",
    @SerializedName("data")
    val data: Map<String, String>? = null,
    @SerializedName("isRead")
    val isRead: Boolean = false,
    @SerializedName("createdAt")
    val createdAt: Long? = null
)

data class NotificationsResponse(
    @SerializedName("notifications")
    val notifications: List<NotificationItem> = emptyList()
)
