package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.remote.movie.MovieApiService
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.util.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

interface MovieRepository {
    suspend fun searchMovies(query: String): Result<List<MovieSearchResult>>
    // lat/lon: opciono, prosleđuje se dalje backend-u za sortiranje bioskopa po udaljenosti.
    suspend fun getMovieDetail(tmdbId: Int, lat: Double? = null, lon: Double? = null): Result<MovieDetail>
    // page: podržava beskonačan skrol na Home-u (HomeViewModel akumulira stranice).
    suspend fun getTrendingMovies(page: Int = 1): Result<List<MovieSearchResult>>
    // Personalizovano (Faza 4) ako je userId poslat i korisnik ima taste profile - backend sam
    // radi fallback na popularne filmove ako ne postoji dovoljno podataka. Videti MovieRouting.kt.
    suspend fun getFeaturedMovies(userId: String?, page: Int = 1): Result<List<MovieSearchResult>>
    suspend fun getFriendsWhoWatched(tmdbId: Int): Result<List<FriendWatched>>
    suspend fun getNowInCinemas(): Result<List<NowInCinemasMovie>>
    // Faza D (17. avgust 2026) - semantička pretraga svedena na filmove trenutno na repertoaru
    // (videti CinemaSemanticIndex na backendu) - ista lista forma kao getNowInCinemas.
    // Faza D dorada - `engine` bira embedding provajder na backendu ("gemini" ili "local"), radi
    // poređenja kvaliteta pretrage.
    suspend fun semanticSearchNowInCinemas(query: String, engine: String = "gemini"): Result<List<NowInCinemasMovie>>
}

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieApiService: MovieApiService
) : MovieRepository {

    override suspend fun searchMovies(query: String): Result<List<MovieSearchResult>> = safeApiCall {
        val response = movieApiService.searchMovies(query)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw Exception("Search failed: ${response.code()}")
        }
    }

    override suspend fun getMovieDetail(tmdbId: Int, lat: Double?, lon: Double?): Result<MovieDetail> = safeApiCall {
        val response = movieApiService.getMovieDetail(tmdbId, lat, lon)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Get detail failed: ${response.code()}")
        }
    }

    override suspend fun getTrendingMovies(page: Int): Result<List<MovieSearchResult>> = safeApiCall {
        val response = movieApiService.getTrendingMovies(page)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw Exception("Trending failed: ${response.code()}")
        }
    }

    override suspend fun getFeaturedMovies(userId: String?, page: Int): Result<List<MovieSearchResult>> = safeApiCall {
        val response = movieApiService.getFeaturedMovies(userId, page)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw Exception("Featured failed: ${response.code()}")
        }
    }

    override suspend fun getFriendsWhoWatched(tmdbId: Int): Result<List<FriendWatched>> = safeApiCall {
        val response = movieApiService.getFriendsWhoWatched(tmdbId)
        if (response.isSuccessful) {
            response.body()?.friends ?: emptyList()
        } else {
            throw Exception("Get friends-watched failed: ${response.code()}")
        }
    }

    override suspend fun getNowInCinemas(): Result<List<NowInCinemasMovie>> = safeApiCall {
        val response = movieApiService.getNowInCinemas()
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw Exception("Get now-in-cinemas failed: ${response.code()}")
        }
    }

    override suspend fun semanticSearchNowInCinemas(query: String, engine: String): Result<List<NowInCinemasMovie>> = safeApiCall {
        val response = movieApiService.semanticSearchNowInCinemas(query, engine)
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw Exception("Semantic search failed: ${response.code()}")
        }
    }
}

