package dev.milinko.movielens.data.pref

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Istorija pretraga (17. avgust 2026, korisnička žalba "prazno dok se ne krene sa pretragom") -
 * čisto lokalno, isti obrazac kao ThemePreferences/UrlStorage (SharedPreferences, radi i za
 * neulogovanog korisnika, nezavisno od mreže). Lista se čuva kao JSON preko Gson-a (isti pristup
 * kao GenericCacheEntity) umesto SharedPreferences `getStringSet` - Set nema garantovan redosled,
 * a ovde nam treba MRU (most-recently-used) redosled.
 *
 * Namerno čuva samo TEKST upita, ne pune rezultate pretrage - jednostavnije, manje podataka za
 * čuvanje, i i dalje daje isti UX (tap na chip ponovo pokreće pretragu, ista logika kao Google/
 * YouTube "nedavne pretrage").
 *
 * Dorada (17. avgust 2026, korisničko pitanje "je l keš vezan za telefon a ne za nalog") - PRVA
 * verzija je čuvala jednu jedinu globalnu listu (jedan SharedPreferences ključ za ceo uređaj), pa
 * bi promena Google naloga na istom telefonu i dalje prikazivala istoriju PRETHODNOG naloga -
 * tačno bug koji je korisnik posumnjao. Za razliku od toga, sve Room keš tabele (watchlist/
 * istorija/GenericCache za profil/prijatelje/taste/Movie Night) su VEĆ bile ispravno skopirane po
 * `userId` (proveren ceo kod - videti MOVIELENS_DESIGN_PLAN.md dorada #6) - ovo je bio jedini
 * stvarni slučaj tog buga, i to u featuru dodatom istog dana. Popravljeno tako što svaka metoda
 * sad prima eksplicitan `scopeId` (userId ili "guest" za neulogovanog korisnika - SearchViewModel
 * ga čita iz SessionManager-a) i gradi poseban SharedPreferences ključ po scope-u - isti princip
 * kao `cacheKey` kod GenericCache-a. Store je namerno "glup" (nema svoj StateFlow/scope state) -
 * SearchViewModel drži svoj lokalni StateFlow i osvežava ga posle svakog poziva, jednostavnije
 * nego da Singleton pokušava da reaktivno prati promenu trenutnog naloga.
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

    fun getQueries(scopeId: String): List<String> {
        val json = prefs.getString(key(scopeId), null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Dodaje upit na vrh (MRU) - uklanja raniji duplikat (bez obzira na velika/mala slova) i seče na MAX_ITEMS. Vraća novo stanje. */
    fun addQuery(scopeId: String, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return getQueries(scopeId)
        val withoutDuplicate = getQueries(scopeId).filterNot { it.equals(trimmed, ignoreCase = true) }
        val updated = (listOf(trimmed) + withoutDuplicate).take(MAX_ITEMS)
        prefs.edit { putString(key(scopeId), gson.toJson(updated)) }
        return updated
    }

    /** Vraća novo stanje (bez uklonjenog upita). */
    fun removeQuery(scopeId: String, query: String): List<String> {
        val updated = getQueries(scopeId).filterNot { it == query }
        prefs.edit { putString(key(scopeId), gson.toJson(updated)) }
        return updated
    }
}
