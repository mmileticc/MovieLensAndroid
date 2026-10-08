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
    // Faza 7 redizajna - kratak, korisnički konfigurabilan "mini status" ispod imena.
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

// Napomena: UserSettings/postavke sa backenda su uklonjene (16. avgust 2026, Faza 2) - backend
// nikad nije imao /settings rutu (potvrđeno pretragom), pa je svaki GET/PUT poziv na nju uvek
// padao. "Tamna tema" je sad čisto lokalno podešavanje (videti data/pref/ThemePreferences.kt).

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
    // Faza 7 redizajna - prijateljev "mini status", isto polje kao sopstveni UserProfile.bio.
    @SerializedName("bio")
    val bio: String? = null,
    @SerializedName("watched_movies")
    val watchedMovies: List<FriendProfileMovie> = emptyList(),
    // E1.5 dorada (2. septembar 2026) - ograničen preview PRE prihvaćenog prijateljstva. Kad
    // friendshipStatus != "ACCEPTED", watchedMovies je uvek prazna (backend je nikad ne popunjava)
    // - email/commonGenres su ono što se umesto toga prikazuje. Videti FriendsRouting.kt na backendu.
    @SerializedName("email")
    val email: String? = null,
    // Bugfix (2. septembar 2026, logcat crash - "Attempt to invoke virtual method
    // 'int java.lang.String.hashCode()' on a null object reference" u FriendProfileScreen.kt).
    // Uzrok: Gson (Retrofit konverter) NE poziva Kotlin konstruktor kad popunjava polja iz JSON-a
    // (koristi refleksiju/Unsafe), pa Kotlin default vrednosti (npr. "ACCEPTED" ovde) VAŽE SAMO
    // pri direktnoj konstrukciji iz koda (npr. .copy(...) u ViewModel-u) - NE i kad polje
    // nedostaje u JSON-u. Stari keš zapisi u Room bazi (snimljeni PRE nego što je ovo polje
    // dodato u E1.5) zato daju friendshipStatus = null uprkos deklarisanom ne-null tipu, što
    // ruši `when` na tom polju (String `when` se kompajlira preko hashCode()). Polja su zato
    // eksplicitno nullable - stvarnost onoga što Gson može da vrati - a mesta koja ih čitaju
    // (FriendProfileScreen.kt) koriste `?:`/`.orEmpty()` fallback.
    @SerializedName("friendship_status")
    val friendshipStatus: String? = "ACCEPTED",
    @SerializedName("common_genres")
    val commonGenres: List<String>? = emptyList()
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
    val rating: Int? = null,
    @SerializedName("review")
    val review: String? = null
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
    // Faza 7 dorada - Profile ekran prikazuje poster umesto plain ikonice/rečenice.
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
    val scores: SuggestionScores = SuggestionScores(),
    // UX dorada (oktobar 2026) - nullable iz istog Gson razloga kao MovieSearchResult.genreIds
    // (stariji backend ih ne šalje).
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
    // Dorada (25. avgust 2026, korisnička žalba "za neke prijatelje se predlozi ponavljaju posle
    // 3-4 ekrana") - da li backend ima još TMDB discover stranica za ovaj par žanrova NAKON ove
    // koja je upravo vraćena. Videti MovieNightViewModel.refreshSuggestions - ranije se umesto
    // ovoga koristio izmišljen fiksan broj stranica (5), pogrešan za većinu parova žanrova.
    @SerializedName("has_more")
    val hasMore: Boolean = false,
    // Zaglavlje Movie Night ekrana (isto za sve predloge) - nullable, videti MovieSuggestion.
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

// Istorija obaveštenja (GET /api/users/{id}/notifications). `data` su isti podaci kao u push poruci.
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
