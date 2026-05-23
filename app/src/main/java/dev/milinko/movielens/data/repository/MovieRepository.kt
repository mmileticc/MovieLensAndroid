package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.remote.movie.MovieApiService
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import javax.inject.Inject
import javax.inject.Singleton

interface MovieRepository {
    suspend fun searchMovies(query: String): Result<List<MovieSearchResult>>
    suspend fun getMovieDetail(tmdbId: Int): Result<MovieDetail>
    suspend fun getTrendingMovies(): Result<List<MovieSearchResult>>
}

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieApiService: MovieApiService
) : MovieRepository {

    override suspend fun searchMovies(query: String): Result<List<MovieSearchResult>> {
        return try {
            val response = movieApiService.searchMovies(query)
            if (response.isSuccessful) {
                Result.success(response.body()?.results ?: emptyList())
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMovieDetail(tmdbId: Int): Result<MovieDetail> {
        return try {
            val response = movieApiService.getMovieDetail(tmdbId)
            if (response.isSuccessful) {
                Result.success(response.body() ?: throw Exception("Empty response"))
            } else {
                Result.failure(Exception("Get detail failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingMovies(): Result<List<MovieSearchResult>> {
        return try {
            val response = movieApiService.getTrendingMovies()
            if (response.isSuccessful) {
                Result.success(response.body()?.results ?: emptyList())
            } else {
                Result.failure(Exception("Trending failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

