package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.remote.movie.MovieApiService
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.util.safeApiCall
import javax.inject.Inject
import javax.inject.Singleton

interface MovieRepository {
    suspend fun searchMovies(query: String): Result<List<MovieSearchResult>>
    suspend fun getMovieDetail(tmdbId: Int): Result<MovieDetail>
    suspend fun getTrendingMovies(): Result<List<MovieSearchResult>>
    suspend fun getFriendsWhoWatched(tmdbId: Int): Result<List<FriendWatched>>
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

    override suspend fun getMovieDetail(tmdbId: Int): Result<MovieDetail> = safeApiCall {
        val response = movieApiService.getMovieDetail(tmdbId)
        if (response.isSuccessful) {
            response.body() ?: throw Exception("Empty response")
        } else {
            throw Exception("Get detail failed: ${response.code()}")
        }
    }

    override suspend fun getTrendingMovies(): Result<List<MovieSearchResult>> = safeApiCall {
        val response = movieApiService.getTrendingMovies()
        if (response.isSuccessful) {
            response.body()?.results ?: emptyList()
        } else {
            throw Exception("Trending failed: ${response.code()}")
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
}

