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
 * Renews the backend JWT (valid 24 hours) transparently.
 *
 * On a 401 it asks Firebase for a fresh ID token (the Firebase SDK keeps a long-lived refresh
 * token), exchanges it for a new backend JWT and retries the request, so the user never notices.
 * If that fails too (the user is really signed out of Firebase), the request stays 401 and the
 * ViewModels show the sign-in prompt.
 *
 * Uses the @AuthRefresh AuthApi (a separate Retrofit/OkHttpClient without this authenticator and
 * without AuthInterceptor); using the main client here would loop on 401 and create a Dagger cycle.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val authRepository: AuthRepository,
    @AuthRefresh private val authApi: AuthApi,
    private val sessionManager: SessionManager
) : Authenticator {

    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Do not try when the user was never signed in, and only once per original request (no
        // endless loop if the refresh keeps failing).
        if (sessionManager.getAppToken() == null) return null
        if (responseCount(response) > 1) return null

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        // Several parallel requests (e.g. Home loads a few lists at once) can get 401 at the same
        // time. Only the first one refreshes; the others wait for the lock and, since the token has
        // already changed, just retry with the new one.
        synchronized(refreshLock) {
            val currentToken = sessionManager.getAppToken() ?: return null
            if (currentToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }
            return refreshAndRetry(response)
        }
    }

    private fun refreshAndRetry(response: Response): Request? {
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
