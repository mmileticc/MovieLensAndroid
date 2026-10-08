package dev.milinko.movielens

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.pref.ThemePreferences
import dev.milinko.movielens.data.push.NotificationPreferences
import dev.milinko.movielens.data.push.PushNotifier
import dev.milinko.movielens.navigation.MovieLensNavHost
import dev.milinko.movielens.navigation.Screen
import dev.milinko.movielens.ui.components.NotificationPermissionPrompt
import dev.milinko.movielens.ui.theme.MovieLensTheme
import dev.milinko.movielens.util.AppLocale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Faza 2 (light tema) - čisto lokalno podešavanje, ne zavisi od logina/mreže, čita se odmah
    // pri pokretanju aktivnosti (videti ThemePreferences.kt).
    @Inject
    lateinit var themePreferences: ThemePreferences

    @Inject
    lateinit var notificationPreferences: NotificationPreferences

    @Inject
    lateinit var sessionManager: SessionManager

    // Ruta iz dodirnutog push obaveštenja; NavHost je otvori čim je spreman.
    private val pendingRoute = mutableStateOf<String?>(null)

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
        pendingRoute.value = intent?.let(::routeFromIntent)

        setContent {
            val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle()
            MovieLensTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                MovieLensNavHost(
                    navController = navController,
                    startDestination = startDestination
                )

                val route = pendingRoute.value
                LaunchedEffect(route) {
                    if (route != null) {
                        navController.navigate(route) { launchSingleTop = true }
                        pendingRoute.value = null
                    }
                }

                // Posle prijave (ponovo se proverava pri svakoj promeni ekrana) jednom se objasni
                // čemu služe obaveštenja, pa se tek onda traži sistemska dozvola.
                val backStackEntry by navController.currentBackStackEntryAsState()
                NotificationPermissionPrompt(
                    trigger = backStackEntry?.destination?.route,
                    isSignedIn = { sessionManager.getUserId() != null },
                    preferences = notificationPreferences
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFromIntent(intent)?.let { pendingRoute.value = it }
    }

    /** Ruta iz dodirnutog obaveštenja ili iz podeljenog linka https://<host>/m/{tmdbId}. */
    private fun routeFromIntent(intent: Intent): String? {
        intent.getStringExtra(PushNotifier.EXTRA_OPEN_ROUTE)?.let { return it }
        val uri = intent.data ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null
        val segments = uri.pathSegments
        val tmdbId = if (segments.size == 2 && segments[0] == "m") segments[1].toIntOrNull() else null
        return tmdbId?.let { Screen.MovieDetail.createRoute(it) }
    }
}
