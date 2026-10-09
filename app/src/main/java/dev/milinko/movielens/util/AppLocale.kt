package dev.milinko.movielens.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.core.content.edit
import java.util.Locale

/** Language choice in Settings. */
enum class AppLanguageOption { SYSTEM, SERBIAN, ENGLISH }

/**
 * The app language. Serbian strings are in `res/values/`, English in `res/values-en/`.
 *
 * A plain `object` with SharedPreferences (not Hilt) on purpose: the language must be known already
 * in `MainActivity.attachBaseContext`, before Hilt injects anything.
 *
 * - [AppLanguageOption.SYSTEM] (default): a phone set to Serbian/Croatian/Bosnian/Montenegrin ->
 *   Serbian, anything else -> English. This cannot be left to Android: e.g. a German phone would
 *   get `values/` (Serbian) because `values-de` does not exist.
 * - Screens get the language through [wrap] (MainActivity); ViewModels and other non-UI code use
 *   [string] (a localized application context).
 */
object AppLocale {
    private const val PREFS = "language_prefs"
    private const val KEY_LANGUAGE = "language"
    private val SERBIAN_FAMILY = setOf("sr", "hr", "bs", "sh", "me", "cnr")

    @Volatile
    private var localizedAppContext: Context? = null

    @Volatile
    private var currentLocale: Locale = Locale.ENGLISH

    /** Called once from Application.onCreate. */
    fun init(appContext: Context) {
        refresh(appContext.applicationContext)
    }

    /** The stored language choice ([AppLanguageOption.SYSTEM] if none). */
    fun getOption(context: Context): AppLanguageOption {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return AppLanguageOption.entries.firstOrNull { it.name == stored } ?: AppLanguageOption.SYSTEM
    }

    /** Stores the choice and refreshes the localized context; the activity should `recreate()` afterwards. */
    fun setOption(context: Context, option: AppLanguageOption) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LANGUAGE, option.name) }
        refresh(context.applicationContext)
    }

    /** "sr" or "en" - sent to the backend as Accept-Language. */
    fun currentTag(): String = currentLocale.language

    /** The resolved locale currently used by the app. */
    fun currentLocale(): Locale = currentLocale

    /** A context with the selected language - for MainActivity.attachBaseContext. */
    fun wrap(base: Context): Context {
        val locale = resolveLocale(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /** Localized text for code without a Compose/Activity context (ViewModels, util). */
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

    // Serbian in Latin script; resources come from the default values/ anyway (there is no values-sr).
    private val SERBIAN: Locale = Locale.forLanguageTag("sr-Latn-RS")
}
