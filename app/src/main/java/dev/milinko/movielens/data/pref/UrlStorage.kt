package dev.milinko.movielens.data.pref

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UrlStorage @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SAVED_ROUTES = "saved_routes"
        private const val DEFAULT_URL = "http://10.0.2.2:8080/"
        private const val MAX_SAVED_ROUTES = 6
    }

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_URL) ?: DEFAULT_URL
    }

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

    fun setBaseUrl(url: String) {
        val formattedUrl = normalizeUrl(url)
        val updatedRoutes = buildList {
            add(formattedUrl)
            getSavedRoutes().forEach { existingRoute ->
                if (existingRoute != formattedUrl) add(existingRoute)
            }
        }.take(MAX_SAVED_ROUTES)

        prefs.edit()
            .putString(KEY_BASE_URL, formattedUrl)
            .putString(KEY_SAVED_ROUTES, JSONArray(updatedRoutes).toString())
            .apply()
    }

    private fun normalizeUrl(url: String): String {
        val trimmedUrl = url.trim().ifBlank { DEFAULT_URL }
        return if (trimmedUrl.endsWith("/")) trimmedUrl else "$trimmedUrl/"
    }
}
