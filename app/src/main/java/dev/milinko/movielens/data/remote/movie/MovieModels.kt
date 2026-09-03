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

// E2.3 (2. septembar 2026) - stranica glumca. `character` je ovde uloga koju je glumac igrao u
// SVAKOM filmu iz njegove filmografije (obrnuto od CastMember.character iznad, koji opisuje
// glumca IZ UGLA jednog filma) - otud posebna, zasebna data klasa umesto ponovne upotrebe CastMember.
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

// Bioskopski termin - polja prate stvarni ScrapedMovie model na backendu (cinema_id/times/city iz
// ranije verzije ovog modela nisu odgovarali stvarnim podacima i nikad nisu bili popunjeni - videti
// MOVIELENS_ROADMAP.md, Faza 4.5). distanceKm je null ako korisnik nije dao lokacijsku dozvolu.
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
    // Kratak tekstualni komentar uz ocenu (opcion) - videti backend HistoryItemDto.review.
    @SerializedName("review")
    val review: String? = null,
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

// "U bioskopima sada" traka na Home-u (Faza 4.5, polish pre redizajna) - iz postojećeg
// skraping repertoara, grupisano po tmdbId sa listom bioskopa.
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
    val cinemas: List<String> = emptyList()
)

data class NowInCinemasResponse(
    @SerializedName("results")
    val results: List<NowInCinemasMovie> = emptyList()
)

// E2.2 dorada (3. septembar 2026) - javni komentari na film, ZAMENJUJE stari HistoryItem.review
// koncept (jedan privatni komentar vezan za ocenu). Potpuno odvojena funkcionalnost - ne zavisi
// od watched/rating statusa, više komentara po korisniku po filmu je dozvoljeno.
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

