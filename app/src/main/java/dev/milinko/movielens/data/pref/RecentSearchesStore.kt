package dev.milinko.movielens.data.pref

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Istorija pretraga (17. avgust 2026, korisnička žalba "prazno dok se ne krene sa pretragom") -
 * čisto lokalno, isti obrazac kao ThemePreferences/UrlStorage (SharedPreferences + StateFlow, radi
 * i za neulogovanog korisnika, nezavisno od mreže/naloga). Lista se čuva kao JSON preko Gson-a
 * (isti pristup kao GenericCacheEntity) umesto SharedPreferences `getStringSet` - Set nema
 * garantovan redosled, a ovde nam treba MRU (most-recently-used) redosled.
 *
 * Namerno čuva samo TEKST upita, ne pune rezultate pretrage - jednostavnije, manje podataka za
 * čuvanje, i i dalje daje isti UX (tap na chip ponovo pokreće pretragu, ista logika kao Google/
 * YouTube "nedavne pretrage").
 */
@Singleton
class RecentSearchesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("recent_searches_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private companion object {
        const val KEY_QUERIES = "recent_queries"
        const val MAX_ITEMS = 8
    }

    private val _recentQueries = MutableStateFlow(readQueries())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    private fun readQueries(): List<String> {
        val json = prefs.getString(KEY_QUERIES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(queries: List<String>) {
        _recentQueries.value = queries
        prefs.edit { putString(KEY_QUERIES, gson.toJson(queries)) }
    }

    /** Dodaje upit na vrh (MRU) - uklanja raniji duplikat (bez obzira na velika/mala slova) i seče na MAX_ITEMS. */
    fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val withoutDuplicate = _recentQueries.value.filterNot { it.equals(trimmed, ignoreCase = true) }
        persist((listOf(trimmed) + withoutDuplicate).take(MAX_ITEMS))
    }

    fun removeQuery(query: String) {
        persist(_recentQueries.value.filterNot { it == query })
    }

    fun clear() {
        persist(emptyList())
    }
}
