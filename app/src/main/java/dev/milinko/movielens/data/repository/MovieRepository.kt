package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.remote.movie.AddCommentRequest
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.movie.LikeCommentResponse
import dev.milinko.movielens.data.remote.movie.MovieApiService
import dev.milinko.movielens.data.remote.movie.MovieComment
import dev.milinko.movielens.data.remote.movie.MovieCommentsResponse
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.remote.movie.PersonDetail
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.util.safeApiCall
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public movie data from the backend: search, details, trending/featured lists, the cinema
 * programme, people and comments. Every call returns a [Result] whose failure carries an
 * HttpException/IOException, which the ViewModels turn into text with `friendlyMessage()`.
 */
interface MovieRepository {
    suspend fun searchMovies(query: String): Result<List<MovieSearchResult>>
    // lat/lon are optional and passed to the backend to sort cinemas by distance.
    suspend fun getMovieDetail(tmdbId: Int, lat: Double? = null, lon: Double? = null): Result<MovieDetail>
    // `page` supports infinite scrolling on Home (HomeViewModel accumulates the pages).
    suspend fun getTrendingMovies(page: Int = 1): Result<List<MovieSearchResult>>
    // Personalized when userId is given and the user has a taste profile; otherwise the backend
    // falls back to popular movies (see MovieRouting.kt).
    suspend fun getFeaturedMovies(userId: String?, page: Int = 1): Result<List<MovieSearchResult>>
    suspend fun getFriendsWhoWatched(tmdbId: Int): Result<List<FriendWatched>>
    suspend fun getNowInCinemas(): Result<List<NowInCinemasMovie>>
    // All TMDB genres, cached in memory after the first successful call.
    suspend fun getGenres(): Result<List<Genre>>
    // Semantic search over the movies currently in cinemas (same shape as getNowInCinemas).
    // `engine` selects the backend's embedding provider ("gemini" or "local").
    suspend fun semanticSearchNowInCinemas(query: String, engine: String = "gemini"): Result<List<NowInCinemasMovie>>
    // Person page: photo, biography, filmography.
    suspend fun getPersonDetail(personId: Int): Result<PersonDetail>

    // Public movie comments (see MovieComment).
    suspend fun getComments(tmdbId: Int, offset: Int = 0, limit: Int = 3): Result<MovieCommentsResponse>
    suspend fun addComment(tmdbId: Int, body: String): Result<MovieComment>
    suspend fun deleteComment(commentId: Int): Result<Unit>
    suspend fun toggleCommentLike(commentId: Int): Result<LikeCommentResponse>
}

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieApiService: MovieApiService
) : MovieRepository {

    // Failed responses always throw a real `HttpException(response)` (never a plain Exception with
    // a message), so `friendlyMessage()` can map the status code (401/403/404/5xx) to a readable
    // message instead of showing raw technical text.

    override suspend fun searchMovies(query: String): Result<List<MovieSearchResult>> = safeApiCall {
        val response = movieApiService.searchMovies(query)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getMovieDetail(tmdbId: Int, lat: Double?, lon: Double?): Result<MovieDetail> = safeApiCall {
        val response = movieApiService.getMovieDetail(tmdbId, lat, lon)
        if (response.isSuccessful) {
            response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getTrendingMovies(page: Int): Result<List<MovieSearchResult>> = safeApiCall {
        val response = movieApiService.getTrendingMovies(page)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getFeaturedMovies(userId: String?, page: Int): Result<List<MovieSearchResult>> = safeApiCall {
        val response = movieApiService.getFeaturedMovies(userId, page)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getFriendsWhoWatched(tmdbId: Int): Result<List<FriendWatched>> = safeApiCall {
        val response = movieApiService.getFriendsWhoWatched(tmdbId)
        if (response.isSuccessful) {
            response.body()?.friends ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    // Genres do not change during a session: one call per app start.
    @Volatile
    private var cachedGenres: List<Genre>? = null

    override suspend fun getGenres(): Result<List<Genre>> {
        cachedGenres?.let { return Result.success(it) }
        return safeApiCall {
            val response = movieApiService.getGenres()
            if (response.isSuccessful) {
                (response.body()?.genres ?: emptyList()).also { if (it.isNotEmpty()) cachedGenres = it }
            } else {
                throw HttpException(response)
            }
        }
    }

    override suspend fun getNowInCinemas(): Result<List<NowInCinemasMovie>> = safeApiCall {
        val response = movieApiService.getNowInCinemas()
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun semanticSearchNowInCinemas(query: String, engine: String): Result<List<NowInCinemasMovie>> = safeApiCall {
        val response = movieApiService.semanticSearchNowInCinemas(query, engine)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getPersonDetail(personId: Int): Result<PersonDetail> = safeApiCall {
        val response = movieApiService.getPersonDetail(personId)
        if (response.isSuccessful) {
            response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun getComments(tmdbId: Int, offset: Int, limit: Int): Result<MovieCommentsResponse> = safeApiCall {
        val response = movieApiService.getComments(tmdbId, offset, limit)
        if (response.isSuccessful) {
            response.body() ?: MovieCommentsResponse()
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun addComment(tmdbId: Int, body: String): Result<MovieComment> = safeApiCall {
        val response = movieApiService.addComment(tmdbId, AddCommentRequest(body))
        if (response.isSuccessful) {
            response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun deleteComment(commentId: Int): Result<Unit> = safeApiCall {
        val response = movieApiService.deleteComment(commentId)
        if (response.isSuccessful) {
            Unit
        } else {
            throw HttpException(response)
        }
    }

    override suspend fun toggleCommentLike(commentId: Int): Result<LikeCommentResponse> = safeApiCall {
        val response = movieApiService.toggleCommentLike(commentId)
        if (response.isSuccessful) {
            response.body() ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
        } else {
            throw HttpException(response)
        }
    }
}
