package dev.milinko.movielens.data.remote.movie

import com.google.gson.annotations.SerializedName

// Search Response
data class MovieSearchResponse(
    @SerializedName("results")
    val results: List<MovieSearchResult> = emptyList()
)

// Search Result - za listu
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
    val overview: String? = null
)

// Movie Detail - za detaljan prikaz
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
    val showtimes: List<Showtime> = emptyList()
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

data class Showtime(
    @SerializedName("cinema_id")
    val cinemaId: Int,
    @SerializedName("cinema_name")
    val cinemaName: String,
    @SerializedName("times")
    val times: List<String> = emptyList(),
    @SerializedName("city")
    val city: String
)

// Watchlist
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
    val priority: Int = 0
)

data class WatchlistResponse(
    @SerializedName("items")
    val items: List<WatchlistItem> = emptyList()
)

// History/Watched
data class HistoryItem(
    @SerializedName("tmdb_id")
    val tmdbId: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("poster_path")
    val posterPath: String? = null,
    @SerializedName("user_rating")
    val userRating: Double,
    @SerializedName("watched_at")
    val watchedAt: Long,
    @SerializedName("duration")
    val duration: Int = 0
)

data class HistoryResponse(
    @SerializedName("items")
    val items: List<HistoryItem> = emptyList()
)

// Trending/Featured
data class TrendingResponse(
    @SerializedName("results")
    val results: List<MovieSearchResult> = emptyList()
)

// Generic API Response wrapper
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

