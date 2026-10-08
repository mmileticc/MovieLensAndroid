package dev.milinko.movielens

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.milinko.movielens.data.push.PushNotifier
import dev.milinko.movielens.data.push.PushTokenManager
import dev.milinko.movielens.util.AppLocale
import javax.inject.Inject

@HiltAndroidApp
class MovieLensApp : Application() {

    @Inject
    lateinit var pushTokenManager: PushTokenManager

    override fun onCreate() {
        super.onCreate()
        // Jezik aplikacije (srpski/engleski) - mora biti spreman pre prvog ViewModel-a/mrežnog poziva.
        AppLocale.init(this)
        PushNotifier.createChannels(this)
        // Već prijavljen korisnik (npr. posle ažuriranja aplikacije) - token se potvrdi na backendu.
        pushTokenManager.syncAsync()
    }
}
