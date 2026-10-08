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
 * Povezuje FCM token ovog uređaja sa prijavljenim nalogom na backendu. Sve ide u pozadini i tiho:
 * bez registrovanog tokena aplikacija radi normalno, samo ne stižu push obaveštenja.
 */
@Singleton
class PushTokenManager @Inject constructor(
    private val userApiService: UserApiService,
    private val sessionManager: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Posle prijave, pri pokretanju aplikacije i kad FCM izda novi token. */
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
     * Pri odjavi. Sesija se briše odmah (korisnik ne čeka mrežu), pa se ovde prosleđuju userId i
     * token odjavljenog naloga - AuthInterceptor ih posle brisanja sesije više ne bi imao.
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
