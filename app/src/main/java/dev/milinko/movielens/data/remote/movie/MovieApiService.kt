package dev.milinko.movielens.data.remote.movie

import dev.milinko.movielens.data.remote.user.FriendsWatchedResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Movie, person, cinema programme and comment endpoints of the backend (`/api/movies`, `/api/people`). */
interface MovieApiService {
    // Search
    @GET("api/movies/search")
    suspend fun searchMovies(
        @Query("query") query: String
    ): Response<MovieSearchResponse>

    // Detail. lat/lon are optional: sent only with location permission, so the backend can sort
    // cinemas by distance.
    @GET("api/movies/{tmdbId}")
    suspend fun getMovieDetail(
        @Path("tmdbId") tmdbId: Int,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): Response<MovieDetail>

    // Trending / featured (featured is personalized when the request carries a token)
    @GET("api/movies/trending")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1
    ): Response<TrendingResponse>

    @GET("api/movies/featured")
    suspend fun getFeaturedMovies(
        @Query("userId") userId: String? = null,
        @Query("page") page: Int = 1
    ): Response<TrendingResponse>

    // "Friends watched" badge - requires sign-in; the backend takes the user from the JWT.
    @GET("api/movies/{tmdbId}/friends-watched")
    suspend fun getFriendsWhoWatched(
        @Path("tmdbId") tmdbId: Int
    ): Response<FriendsWatchedResponse>

    // All TMDB genres (id -> name), for the genre filters on the "See all" screens.
    @GET("api/movies/genres")
    suspend fun getGenres(): Response<GenreListResponse>

    // The whole scraped programme (Home shows the first 20, the "In cinemas" screen all of it).
    @GET("api/movies/now-in-cinemas")
    suspend fun getNowInCinemas(): Response<NowInCinemasResponse>

    // Semantic ("mood") search over the movies currently in cinemas; same response shape as
    // getNowInCinemas. `engine` ("gemini" or "local") selects the backend's embedding model.
    @GET("api/movies/now-in-cinemas/semantic-search")
    suspend fun semanticSearchNowInCinemas(
        @Query("q") query: String,
        @Query("engine") engine: String = "gemini"
    ): Response<NowInCinemasResponse>

    // Person page (actor/crew). Public.
    @GET("api/people/{id}")
    suspend fun getPersonDetail(
        @Path("id") personId: Int
    ): Response<PersonDetail>

    // Movie comments. Reading is public (with a token the own/friend/liked markers are filled in);
    // posting, deleting and liking require sign-in.
    @GET("api/movies/{tmdbId}/comments")
    suspend fun getComments(
        @Path("tmdbId") tmdbId: Int,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 3
    ): Response<MovieCommentsResponse>

    @POST("api/movies/{tmdbId}/comments")
    suspend fun addComment(
        @Path("tmdbId") tmdbId: Int,
        @Body request: AddCommentRequest
    ): Response<MovieComment>

    @DELETE("api/comments/{id}")
    suspend fun deleteComment(
        @Path("id") commentId: Int
    ): Response<Unit>

    @POST("api/comments/{id}/like")
    suspend fun toggleCommentLike(
        @Path("id") commentId: Int
    ): Response<LikeCommentResponse>
}

