package dev.milinko.movielens.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Receives FCM messages (also in the background - the backend sends high-priority data messages). */
@AndroidEntryPoint
class MovieneeMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    @Inject
    lateinit var notificationPreferences: NotificationPreferences

    override fun onNewToken(token: String) {
        pushTokenManager.syncAsync(knownToken = token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifier.show(this, message.data, notificationPreferences)
    }
}
