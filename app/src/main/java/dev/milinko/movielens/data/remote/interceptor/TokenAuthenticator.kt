package dev.milinko.movielens.data.remote.interceptor

import dev.milinko.movielens.data.auth.AuthRepository
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.AuthApi
import dev.milinko.movielens.data.remote.AuthFirebaseRequest
import dev.milinko.movielens.di.AuthRefresh
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backend JWT (izdat u Routing.kt) traje 24h. Bez ovoga, kad istekne, SVAKI zaštićeni poziv
 * (watchlist, history, profile...) tiho puca sa 401 zauvek - SessionManager.isSessionValid()
 * samo proverava da li token POSTOJI, ne da li je istekao, pa korisnik ostaje "prijavljen" u UI-ju
 * dok mu ništa ne radi dok se ručno ne odjavi/prijavi.
 *
 * Ovaj Authenticator hvata 401 odgovore, tiho traži svež Firebase ID token (Firebase SDK sam
 * održava refresh token koji traje mnogo duže od 24h) i njime ponovo razmenjuje novi backend JWT
 * (POST /api/auth/firebase), pa ponavlja originalni zahtev sa novim tokenom - korisnik ništa ne
 * primeti. Ako i to ne uspe (npr. korisnik stvarno nije prijavljen na Firebase-u), odustaje i
 * zahtev ostaje 401 - postojeći "traži prijavu" tok u ViewModel-ima to hvata.
 *
 * Koristi @AuthRefresh AuthApi (poseban Retrofit/OkHttpClient BEZ ovog Authenticator-a i BEZ
 * AuthInterceptor-a) da se izbegne beskonačna petlja/Dagger ciklus - da smo ovde koristili glavni
 * OkHttpClient, on bi opet prošao kroz ovaj isti Authenticator na 401.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authRepository: AuthRepository,
    @AuthRefresh private val authApi: AuthApi,
    private val sessionManager: SessionManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Ne pokušavaj ako korisnik nikad nije bio prijavljen, i ne pokušavaj više od jednom po
        // originalnom zahtevu (sprečava beskonačnu petlju ako je refresh i dalje neuspešan).
        if (sessionManager.getAppToken() == null) return null
        if (responseCount(response) > 1) return null

        return runBlocking {
            try {
                val firebaseIdToken = authRepository.getFirebaseIdToken(forceRefresh = true) ?: return@runBlocking null
                val exchange = authApi.exchangeFirebaseToken(AuthFirebaseRequest(firebaseIdToken))
                val newToken = exchange.appToken
                val user = exchange.user
                if (newToken != null && user != null) {
                    sessionManager.saveSession(
                        appToken = newToken,
                        userId = user.id,
                        firebaseUid = user.firebaseUid,
                        username = user.username,
                        email = user.email,
                        avatarUrl = user.avatarUrl
                    )
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
