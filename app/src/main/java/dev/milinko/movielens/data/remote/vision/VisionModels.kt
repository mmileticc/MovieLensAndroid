package dev.milinko.movielens.data.remote.vision

import com.google.gson.annotations.SerializedName

data class VisionSearchResponse(
    @SerializedName("notMovieLike")
    val notMovieLike: Boolean,
    @SerializedName("uncertaintyReason")
    val uncertaintyReason: String? = null,
    @SerializedName("queryLanguage")
    val queryLanguage: String? = null,
    @SerializedName("resolutions")
    val resolutions: List<CandidateResolution> = emptyList()
)

data class CandidateResolution(
    @SerializedName("candidate")
    val candidate: MovieCandidate,
    @SerializedName("matches")
    val matches: List<TmdbMatch> = emptyList()
)

data class MovieCandidate(
    @SerializedName("title")
    val title: String,
    @SerializedName("year")
    val year: Int? = null,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("confidence")
    val confidence: Double? = null,
    @SerializedName("notes")
    val notes: String? = null
)

data class TmdbMatch(
    @SerializedName("id")
    val id: Int,
    @SerializedName("title")
    val title: String,
    @SerializedName("originalTitle")
    val originalTitle: String? = null,
    @SerializedName("releaseDate")
    val releaseDate: String? = null,
    @SerializedName("overview")
    val overview: String? = null,
    @SerializedName("posterPath")
    val posterPath: String? = null,
    @SerializedName("popularity")
    val popularity: Double? = null,
    @SerializedName("voteAverage")
    val voteAverage: Double? = null
)

data class ErrorResponse(
    @SerializedName("error")
    val error: String
)

fun tmdbPosterUrl(path: String?, size: String = "w500"): String? =
    path?.let { "https://image.tmdb.org/t/p/$size$it" }

