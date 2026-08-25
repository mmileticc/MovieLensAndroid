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

    // Detail. lat/lon: opciono, samo ako korisnik ima uključenu lokacijsku dozvolu - backend onda
    // sortira "Bioskopski termini" po udaljenosti.
    @GET("api/movies/{tmdbId}")
    suspend fun getMovieDetail(
        @Path("tmdbId") tmdbId: Int,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): Response<MovieDetail>

    // Trending/Featured
    @GET("api/movies/trending")
    suspend fun getTrendingMovies(
        @Query("page") page: Int = 1
    ): Response<TrendingResponse>

    @GET("api/movies/featured")
    suspend fun getFeaturedMovies(
        @Query("userId") userId: String? = null,
        @Query("page") page: Int = 1
    ): Response<TrendingResponse>

    // "Prijatelji su gledali" bedž - JWT zaštićena ruta (za razliku od ostalih ruta u ovom
    // interfejsu koje su javne), userId se čita iz JWT-a na backendu.
    @GET("api/movies/{tmdbId}/friends-watched")
    suspend fun getFriendsWhoWatched(
        @Path("tmdbId") tmdbId: Int
    ): Response<FriendsWatchedResponse>

    // "U bioskopima sada" traka na Home-u - javna ruta, iz postojećeg skraping repertoara.
    @GET("api/movies/now-in-cinemas")
    suspend fun getNowInCinemas(): Response<NowInCinemasResponse>

    // Faza D (17. avgust 2026, predlog mentora Marka) - semantička pretraga, namerno svedena na
    // filmove trenutno na repertoaru (videti CinemaSemanticIndex na backendu). Ista odgovor forma
    // kao gornja now-in-cinemas ruta.
    //
    // Faza D dorada - opcioni `engine` parametar ("gemini" ili "local") bira koji embedding se
    // koristi na backendu, radi poređenja kvaliteta pretrage. Podrazumevano "gemini" na klijentu.
    @GET("api/movies/now-in-cinemas/semantic-search")
    suspend fun semanticSearchNowInCinemas(
        @Query("q") query: String,
        @Query("engine") engine: String = "gemini"
    ): Response<NowInCinemasResponse>
}

