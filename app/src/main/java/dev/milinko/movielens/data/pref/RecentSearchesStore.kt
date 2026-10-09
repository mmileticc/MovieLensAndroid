package dev.milinko.movielens.data.pref

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recent search queries, stored locally in SharedPreferences (works signed out and offline).
 *
 * - Stored as a JSON list (Gson) rather than `getStringSet`, because a Set has no order and the
 *   list must be most-recently-used first.
 * - Only the query text is stored, not the results; tapping a chip simply runs the search again.
 * - Scoped per account: every method takes a `scopeId` (the user id, or "guest" when signed out),
 *   so a different account on the same phone never sees someone else's history.
 *
 * The store is deliberately stateless; SearchViewModel keeps its own StateFlow and updates it with
 * the list returned by each call.
 */
@Singleton
class RecentSearchesStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("recent_searches_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private companion object {
        const val MAX_ITEMS = 8
    }

    private fun key(scopeId: String) = "recent_queries:$scopeId"

    /** Queries for [scopeId], most recent first. */
    fun getQueries(scopeId: String): List<String> {
        val json = prefs.getString(key(scopeId), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Adds the query at the top (removing an earlier duplicate, case-insensitive), trims to MAX_ITEMS and returns the new list. */
    fun addQuery(scopeId: String, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getQueries(scopeId)
        val withoutDuplicate = getQueries(scopeId).filterNot { it.equals(trimmed, ignoreCase = true) }
        val updated = (listOf(trimmed) + withoutDuplicate).take(MAX_ITEMS)
        prefs.edit { putString(key(scopeId), gson.toJson(updated)) }
        return updated
    }

    /** Removes the query and returns the new list. */
    fun removeQuery(scopeId: String, query: String): List<String> {
        val updated = getQueries(scopeId).filterNot { it == query }
        prefs.edit { putString(key(scopeId), gson.toJson(updated)) }
        return updated
    }

    /** Clears the history for [scopeId] and returns the (empty) list. */
    fun clearQueries(scopeId: String): List<String> {
        prefs.edit { remove(key(scopeId)) }
        return emptyList()
    }
}
