package dev.milinko.movielens.data.pref

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * Backend base URL and the list of saved URLs (Render, local, ngrok...). The URL can be changed
 * at runtime from the developer settings in debug builds; every request picks it up through
 * DynamicBaseUrlInterceptor.
 */
@Singleton
class UrlStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SAVED_ROUTES = "saved_routes"
        // The hosted (Render) backend, so the app works out of the box for testers.
        private const val DEFAULT_URL = "https://movielensbackendktor.onrender.com/"
        private const val MAX_SAVED_ROUTES = 6

        /** For code outside the Hilt graph (e.g. a composable showing an avatar); same value as [getBaseUrl]. */
        fun readBaseUrl(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    /** The currently selected backend URL. */
    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

    /** Saved URLs, the current one first (at most MAX_SAVED_ROUTES). */
    fun getSavedRoutes(): List<String> {
        val storedRoutes = prefs.getString(KEY_SAVED_ROUTES, null) ?: return listOf(getBaseUrl())
        val routes = runCatching {
            JSONArray(storedRoutes).let { jsonArray ->
                buildList {
                    for (i in 0 until jsonArray.length()) {
                        val route = jsonArray.optString(i).trim()
                        if (route.isNotBlank()) add(route)
                    }
                }
            }
        }.getOrElse { emptyList() }

        val activeUrl = getBaseUrl()
        return when {
            routes.isEmpty() -> listOf(activeUrl)
            activeUrl !in routes -> listOf(activeUrl) + routes
            else -> routes
        }.distinct().take(MAX_SAVED_ROUTES)
    }

    /** Selects [url] as the backend and moves it to the top of the saved list. */
    fun setBaseUrl(url: String) {
        val formattedUrl = normalizeUrl(url)
        val updatedRoutes = buildList {
            add(formattedUrl)
            getSavedRoutes().forEach { existingRoute ->
                if (existingRoute != formattedUrl) add(existingRoute)
            }
        }.take(MAX_SAVED_ROUTES)

        prefs.edit {
            putString(KEY_BASE_URL, formattedUrl)
                .putString(KEY_SAVED_ROUTES, JSONArray(updatedRoutes).toString())
        }
    }

    /** Adds [url] to the saved list without selecting it. */
    fun addRoute(url: String) {
        val formattedUrl = normalizeUrl(url)
        val updatedRoutes = buildList {
            add(formattedUrl)
            getSavedRoutes().forEach { existingRoute ->
                if (existingRoute != formattedUrl) add(existingRoute)
            }
        }.take(MAX_SAVED_ROUTES)

        prefs.edit {
            putString(KEY_SAVED_ROUTES, JSONArray(updatedRoutes).toString())
        }
    }

    /** Removes [url]; if it was selected, the next saved URL (or the default) becomes current. */
    fun removeRoute(url: String) {
        val formattedUrl = normalizeUrl(url)
        val remainingRoutes = getSavedRoutes().filterNot { it == formattedUrl }

        val fallbackUrl = remainingRoutes.firstOrNull() ?: DEFAULT_URL
        val currentBaseUrl = getBaseUrl()

        prefs.edit {
            putString(
                KEY_SAVED_ROUTES,
                JSONArray(remainingRoutes.ifEmpty { listOf(fallbackUrl) }).toString()
            )
                .putString(
                    KEY_BASE_URL,
                    if (currentBaseUrl == formattedUrl) fallbackUrl else currentBaseUrl
                )
        }
    }

    private fun normalizeUrl(url: String): String {
        val trimmedUrl = url.trim().ifBlank { DEFAULT_URL }
        return if (trimmedUrl.endsWith("/")) trimmedUrl else "$trimmedUrl/"
    }
}
