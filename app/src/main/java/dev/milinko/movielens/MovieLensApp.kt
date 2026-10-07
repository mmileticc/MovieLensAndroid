package dev.milinko.movielens

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.milinko.movielens.util.AppLocale

@HiltAndroidApp
class MovieLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Jezik aplikacije (srpski/engleski) - mora biti spreman pre prvog ViewModel-a/mrežnog poziva.
        AppLocale.init(this)
    }
}
