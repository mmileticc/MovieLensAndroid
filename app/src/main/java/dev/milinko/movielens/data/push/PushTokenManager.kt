package dev.milinko.movielens.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.user.DeviceTokenBody
import dev.milinko.movielens.data.remote.user.UserApiService
import dev.milinko.movielens.util.awaitTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Links this device's FCM token to the signed-in account on the backend. Everything runs silently
 * in the background: without a registered token the app works normally, only pushes do not arrive.
 */
@Singleton
class PushTokenManager @Inject constructor(
    private val userApiService: UserApiService,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Registers the token: after sign-in, on app start and when FCM issues a new token. */
    fun syncAsync(knownToken: String? = null) {
        val userId = sessionManager.getUserId() ?: return
        scope.launch {
            runCatching {
                val token = knownToken ?: FirebaseMessaging.getInstance().token.awaitTask()
                userApiService.registerDeviceToken(userId, DeviceTokenBody(token))
            }.onFailure { Log.w(TAG, "Registracija FCM tokena nije uspela: ${it.message}") }
        }
    }

    /**
     * Unregisters the token on sign-out. The session is cleared immediately (the user does not wait
     * for the network), so the signed-out account's userId and token are passed explicitly -
     * AuthInterceptor no longer has them.
     */
    fun unregisterAsync(userId: String, appToken: String) {
        scope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.awaitTask()
                userApiService.unregisterDeviceToken(userId, token, "Bearer $appToken")
            }.onFailure { Log.w(TAG, "Odjava FCM tokena nije uspela: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "PushTokenManager"
    }
}
