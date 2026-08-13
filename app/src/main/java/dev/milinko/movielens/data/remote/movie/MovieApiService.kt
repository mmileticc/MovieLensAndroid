package dev.milinko.movielens.data.remote.movie

import dev.milinko.movielens.data.remote.user.FriendsWatchedResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MovieApiService {
    // Search
    @GET("api/movies/search")
    suspend fun searchMovies(
        @Query("query") query: String
    ): Response<MovieSearchResponse>

    // Detail
    @GET("api/movies/{tmdbId}")
    suspend fun getMovieDetail(
        @Path("tmdbId") tmdbId: Int
    ): Response<MovieDetail>

    // Trending/Featured
    @GET("api/movies/trending")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1
    ): Response<TrendingResponse>

    @GET("api/movies/featured")
    suspend fun getFeaturedMovies(
        @Query("userId") userId: String? = null
    ): Response<TrendingResponse>

    // "Prijatelji su gledali" bedž - JWT zaštićena ruta (za razliku od ostalih ruta u ovom
    // interfejsu koje su javne), userId se čita iz JWT-a na backendu.
    @GET("api/movies/{tmdbId}/friends-watched")
    suspend fun getFriendsWhoWatched(
        @Path("tmdbId") tmdbId: Int
    ): Response<FriendsWatchedResponse>
}

