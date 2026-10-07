package dev.milinko.movielens

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.milinko.movielens.data.pref.ThemePreferences
import dev.milinko.movielens.navigation.MovieLensNavHost
import dev.milinko.movielens.navigation.Screen
import dev.milinko.movielens.ui.theme.MovieLensTheme
import dev.milinko.movielens.util.AppLocale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Faza 2 (light tema) - čisto lokalno podešavanje, ne zavisi od logina/mreže, čita se odmah
    // pri pokretanju aktivnosti (videti ThemePreferences.kt).
    @Inject
    lateinit var themePreferences: ThemePreferences

    // Jezik aplikacije (srpski/engleski) - ceo UI ove aktivnosti čita resurse iz ovog konteksta.
    // Promena jezika u Podešavanjima radi recreate() aktivnosti (videti AppLocale).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // App is usable without login; auth is required only for scan action.
        val startDestination = Screen.Home.route

        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsState()
            MovieLensTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                MovieLensNavHost(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
