package dev.milinko.movielens.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import java.util.Locale

/** Izbor jezika u Podešavanjima. */
enum class AppLanguageOption { SYSTEM, SERBIAN, ENGLISH }

/**
 * Jezik aplikacije (oktobar 2026 - dodat engleski). Srpski tekstovi su u `res/values/`, engleski u
 * `res/values-en/`.
 *
 * Namerno običan `object` sa SharedPreferences (ne Hilt): jezik mora biti poznat već u
 * `MainActivity.attachBaseContext`, pre nego što Hilt ubaci zavisnosti.
 *
 * - [AppLanguageOption.SYSTEM] (podrazumevano): telefon na srpskom/hrvatskom/bosanskom/
 *   crnogorskom -> srpski, sve ostalo -> engleski. Ne može se prepustiti samom Android-u: za npr.
 *   nemački telefon bi uzeo `values/` (srpski) jer `values-de` ne postoji.
 * - Ekrani dobijaju jezik preko [wrap] (MainActivity), a ViewModel-i i ostali ne-UI kod preko
 *   [string] (lokalizovan aplikacioni kontekst).
 */
object AppLocale {
    private const val PREFS = "language_prefs"
    private const val KEY_LANGUAGE = "language"
    private val SERBIAN_FAMILY = setOf("sr", "hr", "bs", "sh", "me", "cnr")

    @Volatile
    private var localizedAppContext: Context? = null

    @Volatile
    private var currentLocale: Locale = Locale.ENGLISH

    /** Poziva se jednom iz Application.onCreate. */
    fun init(appContext: Context) {
        refresh(appContext.applicationContext)
    }

    fun getOption(context: Context): AppLanguageOption {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return AppLanguageOption.entries.firstOrNull { it.name == stored } ?: AppLanguageOption.SYSTEM
    }

    /** Čuva izbor i osvežava lokalizovan kontekst; aktivnost posle toga treba `recreate()`. */
    fun setOption(context: Context, option: AppLanguageOption) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LANGUAGE, option.name) }
        refresh(context.applicationContext)
    }

    /** "sr" ili "en" - šalje se backendu kao Accept-Language. */
    fun currentTag(): String = currentLocale.language

    fun currentLocale(): Locale = currentLocale

    /** Kontekst sa izabranim jezikom - za MainActivity.attachBaseContext. */
    fun wrap(base: Context): Context {
        val locale = resolveLocale(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Lokalizovan tekst za kod koji nema Compose/Activity kontekst (ViewModel-i, util). */
    fun string(@StringRes id: Int, vararg args: Any): String {
        val ctx = localizedAppContext ?: return Resources.getSystem().getString(android.R.string.unknownName)
        return if (args.isEmpty()) ctx.getString(id) else ctx.getString(id, *args)
    }

    private fun refresh(appContext: Context) {
        val locale = resolveLocale(appContext)
        currentLocale = locale
        val config = Configuration(appContext.resources.configuration)
        config.setLocales(LocaleList(locale))
        localizedAppContext = appContext.createConfigurationContext(config)
    }

    private fun resolveLocale(context: Context): Locale = when (getOption(context)) {
        AppLanguageOption.SERBIAN -> SERBIAN
        AppLanguageOption.ENGLISH -> Locale.ENGLISH
        AppLanguageOption.SYSTEM -> {
            val systemLanguage = Resources.getSystem().configuration.locales[0].language
            if (systemLanguage in SERBIAN_FAMILY) SERBIAN else Locale.ENGLISH
        }
    }

    // Srpski latinicom - za resurse se ionako uzima podrazumevani values/ (nema values-sr).
    private val SERBIAN: Locale = Locale.forLanguageTag("sr-Latn-RS")
}
