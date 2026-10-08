package dev.milinko.movielens.data.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Prima FCM poruke (i kad je aplikacija u pozadini - backend šalje data poruke visokog prioriteta). */
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
