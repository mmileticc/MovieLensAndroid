package dev.milinko.movielens.data.remote.vision

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisionSearchResponse(
    @SerialName("notMovieLike") val notMovieLike: Boolean,
    @SerialName("uncertaintyReason") val uncertaintyReason: String? = null,
    @SerialName("queryLanguage") val queryLanguage: String? = null,
    @SerialName("resolutions") val resolutions: List<CandidateResolution> = emptyList()
)

@Serializable
data class CandidateResolution(
    @SerialName("candidate") val candidate: MovieCandidate,
    @SerialName("matches") val matches: List<TmdbMatch>
)

@Serializable
data class MovieCandidate(
    @SerialName("title") val title: String,
    @SerialName("year") val year: Int? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("confidence") val confidence: Double? = null,
    @SerialName("notes") val notes: String? = null
)

@Serializable
data class TmdbMatch(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String,
    @SerialName("originalTitle") val originalTitle: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("popularity") val popularity: Double? = null,
    @SerialName("voteAverage") val voteAverage: Double? = null,
    // KLJUČNI DEO: Lista lokalnih bioskopa u kojima film igra
    @SerialName("cinemaShowtimes") val cinemaShowtimes: List<ScrapedMovie> = emptyList()
)

@Serializable
data class ScrapedMovie(
    @SerialName("title") val title: String,
    @SerialName("originalTitle") val originalTitle: String? = null,
    @SerialName("cinemaName") val cinemaName: String,
    @SerialName("cinemaLocation") val cinemaLocation: String,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("bookingUrl") val bookingUrl: String,
    @SerialName("isUpcoming") val isUpcoming: Boolean
)
data class ErrorResponse(
    @SerialName("error")
    val error: String
)

fun tmdbPosterUrl(path: String?, size: String = "w500"): String? =
    path?.let { "https://image.tmdb.org/t/p/$size$it" }

