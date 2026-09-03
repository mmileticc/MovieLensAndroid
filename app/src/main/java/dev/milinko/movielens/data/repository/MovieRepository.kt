package dev.milinko.movielens.data.repository

import dev.milinko.movielens.data.remote.movie.MovieApiService
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
    // E2.3 (2. septembar 2026) - stranica glumca (foto, biografija, filmografija).
    suspend fun getPersonDetail(personId: Int): Result<PersonDetail>
}

@Singleton
class MovieRepositoryImpl @Inject constructor(
    private val movieApiService: MovieApiService
) : MovieRepository {

    // Dorada (2. septembar 2026, korisnički zahtev - "nijedna sirova poruka ne sme da postoji
    // nigde") - SVE metode ispod su ranije na neuspešan odgovor bacale generički `Exception("X
    // failed: ${response.code()}")` - taj tekst NIJE HttpException, pa `friendlyMessage()`
    // (util/FriendlyError.kt) nije mogao da prepozna status kod i taj sirovi engleski tekst je
    // prolazio direktno do korisnika. Sad se svuda baca pravi `HttpException(response)` - isti
    // obrazac kao `VisionRepository`/`UserRepository` - da gornja HTTP-kod grana u
    // `friendlyMessage()` stvarno uhvati 401/403/404/5xx i vrati razumljivu srpsku poruku.

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
            response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
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
            response.body() ?: throw IllegalStateException("Server je vratio prazan odgovor.")
        } else {
            throw HttpException(response)
        }
    }
}
