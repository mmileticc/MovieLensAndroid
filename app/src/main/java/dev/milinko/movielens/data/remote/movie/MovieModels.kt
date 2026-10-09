package dev.milinko.movielens.data.remote.movie

import com.google.gson.annotations.SerializedName

/** Response of the movie search. */
data class MovieSearchResponse(
    @SerializedName("results")
    val results: List<MovieSearchResult> = emptyList()
)

/** A movie in a list (search, trending, suggestions). */
data class MovieSearchResult(
    @SerializedName("id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("release_date")
    val releaseDate: String? = null,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("vote_average")
    val voteAverage: Double = 0.0,
    @SerializedName("overview")
    val overview: String? = null,
    // New fields are nullable on purpose: Gson bypasses Kotlin default values when a class has no
    // no-arg constructor, so a field an older backend does not send would be null despite a
    // non-null type. Read with .orEmpty().
    @SerializedName("genre_ids")
    val genreIds: List<Int>? = null
)

/** Full movie details for the detail screen. */
data class MovieDetail(
    @SerializedName("id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("release_date")
    val releaseDate: String? = null,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("backdrop_path")
    val backdropPath: String? = null,
    @SerializedName("overview")
    val overview: String? = null,
    @SerializedName("vote_average")
    val voteAverage: Double = 0.0,
    @SerializedName("runtime")
    val runtime: Int = 0,
    @SerializedName("genres")
    val genres: List<Genre> = emptyList(),
    @SerializedName("cast")
    val cast: List<CastMember> = emptyList(),
    @SerializedName("showtimes")
    val showtimes: List<Showtime> = emptyList(),
    // Extra ratings from OMDb; null when the backend has no OMDb key or OMDb has no data (then
    // they are simply not shown).
    @SerializedName("imdb_rating")
    val imdbRating: Double? = null,
    @SerializedName("rotten_tomatoes")
    val rottenTomatoes: String? = null,
    // "Explore more" section - nullable for the same reason as MovieSearchResult.genreIds.
    @SerializedName("imdb_id")
    val imdbId: String? = null,
    @SerializedName("videos")
    val videos: List<MovieVideo>? = null,
    @SerializedName("backdrops")
    val backdrops: List<String>? = null
)

/** A YouTube trailer or teaser; `key` is the YouTube video id. */
data class MovieVideo(
    @SerializedName("key")
    val key: String,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("type")
    val type: String? = null
)

data class GenreListResponse(
    @SerializedName("genres")
    val genres: List<Genre> = emptyList()
)

data class Genre(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String
)

data class CastMember(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("character")
    val character: String? = null,
    @SerializedName("profile_path")
    val profilePath: String? = null
)

/**
 * One movie of a person's filmography. `character` is the role in this movie (the opposite view
 * of [CastMember], which describes an actor from one movie's point of view).
 */
data class PersonMovieCredit(
    @SerializedName("id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("character")
    val character: String? = null,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("release_date")
    val releaseDate: String? = null,
    @SerializedName("vote_average")
    val voteAverage: Double? = null
)

data class PersonDetail(
    @SerializedName("id")
    val id: Int,
    @SerializedName("name")
    val name: String,
    @SerializedName("biography")
    val biography: String? = null,
    @SerializedName("birthday")
    val birthday: String? = null,
    @SerializedName("deathday")
    val deathday: String? = null,
    @SerializedName("place_of_birth")
    val placeOfBirth: String? = null,
    @SerializedName("profile_path")
    val profilePath: String? = null,
    @SerializedName("known_for_department")
    val knownForDepartment: String? = null,
    @SerializedName("movie_credits")
    val movieCredits: List<PersonMovieCredit> = emptyList()
)

/**
 * A cinema showing the movie (from the backend's scraped programme). `distanceKm` is null without
 * location permission.
 */
data class Showtime(
    @SerializedName("cinema_name")
    val cinemaName: String,
    @SerializedName("cinema_location")
    val cinemaLocation: String,
    @SerializedName("booking_url")
    val bookingUrl: String,
    @SerializedName("latitude")
    val latitude: Double? = null,
    @SerializedName("longitude")
    val longitude: Double? = null,
    @SerializedName("distance_km")
    val distanceKm: Double? = null,
    @SerializedName("is_upcoming")
    val isUpcoming: Boolean = false
)

/** A movie on the user's watchlist. */
data class WatchlistItem(
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("added_at")
    val addedAt: Long,
    @SerializedName("priority")
    val priority: Int = 0,
    // For the Library filters and sorting - nullable for the same Gson reason as MovieSearchResult.genreIds.
    @SerializedName("genres")
    val genres: List<Genre>? = null,
    @SerializedName("release_year")
    val releaseYear: Int? = null,
    @SerializedName("vote_average")
    val voteAverage: Double? = null
)

data class WatchlistResponse(
    @SerializedName("items")
    val items: List<WatchlistItem> = emptyList()
)

/** A watched (and possibly rated) movie. */
data class HistoryItem(
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("user_rating")
    val userRating: Double,
    // Optional note attached to the rating (see the backend's HistoryItemDto.review).
    @SerializedName("review")
    val review: String? = null,
    @SerializedName("watched_at")
    val watchedAt: Long,
    @SerializedName("duration")
    val duration: Int = 0,
    @SerializedName("genres")
    val genres: List<Genre>? = null,
    @SerializedName("release_year")
    val releaseYear: Int? = null,
    @SerializedName("vote_average")
    val voteAverage: Double? = null
)

data class HistoryResponse(
    @SerializedName("items")
    val items: List<HistoryItem> = emptyList()
)

/** Response of trending / featured. */
data class TrendingResponse(
    @SerializedName("results")
    val results: List<MovieSearchResult> = emptyList()
)

/** A movie in the current cinema programme with the cinemas showing it. */
data class NowInCinemasMovie(
    @SerializedName("id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("vote_average")
    val voteAverage: Double = 0.0,
    @SerializedName("cinemas")
    val cinemas: List<String> = emptyList(),
    // Fields for the "In cinemas" screen - nullable for the same reason as MovieSearchResult.genreIds.
    @SerializedName("genres")
    val genres: List<Genre>? = null,
    @SerializedName("release_date")
    val releaseDate: String? = null,
    @SerializedName("overview")
    val overview: String? = null,
    @SerializedName("runtime")
    val runtime: Int? = null,
    @SerializedName("is_upcoming")
    val isUpcoming: Boolean? = null
)

data class NowInCinemasResponse(
    @SerializedName("results")
    val results: List<NowInCinemasMovie> = emptyList()
)

/**
 * A public comment on a movie. Independent of ratings and watched status; a user can leave
 * several comments on the same movie.
 */
data class MovieComment(
    @SerializedName("id")
    val id: Int,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("username")
    val username: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("body")
    val body: String,
    @SerializedName("created_at")
    val createdAt: Long,
    @SerializedName("like_count")
    val likeCount: Int = 0,
    @SerializedName("liked_by_me")
    val likedByMe: Boolean = false,
    @SerializedName("is_friend")
    val isFriend: Boolean = false,
    @SerializedName("is_own")
    val isOwn: Boolean = false
)

data class MovieCommentsResponse(
    @SerializedName("comments")
    val comments: List<MovieComment> = emptyList(),
    @SerializedName("has_more")
    val hasMore: Boolean = false
)

data class AddCommentRequest(
    @SerializedName("body")
    val body: String
)

data class LikeCommentResponse(
    @SerializedName("liked_by_me")
    val likedByMe: Boolean,
    @SerializedName("like_count")
    val likeCount: Int
)

/** Generic API response wrapper. */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("data")
    val data: T? = null,
    @SerializedName("error")
    val error: ErrorWrapper? = null,
    @SerializedName("timestamp")
    val timestamp: Long? = null
)

data class ErrorWrapper(
    @SerializedName("code")
    val code: String,
    @SerializedName("message")
    val message: String,
    @SerializedName("details")
    val details: String? = null
)

