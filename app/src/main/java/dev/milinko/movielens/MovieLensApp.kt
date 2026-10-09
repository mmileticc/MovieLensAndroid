package dev.milinko.movielens

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.milinko.movielens.data.push.PushNotifier
import dev.milinko.movielens.data.push.PushTokenManager
import dev.milinko.movielens.util.AppLocale
import javax.inject.Inject

/** Application class: Hilt entry point; initializes the app language and syncs the push token. */
@HiltAndroidApp
class MovieLensApp : Application() {

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    override fun onCreate() {
        super.onCreate()
        // App language (Serbian/English) - must be ready before the first ViewModel or network call.
        AppLocale.init(this)
        PushNotifier.createChannels(this)
        // An already signed-in user (e.g. after an app update) - confirm the token on the backend.
        pushTokenManager.syncAsync()
    }
}
