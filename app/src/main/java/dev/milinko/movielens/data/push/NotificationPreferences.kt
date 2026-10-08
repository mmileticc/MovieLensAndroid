package dev.milinko.movielens.data.push

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Vrste obaveštenja - ista imena šalje backend u polju `type` (PushService.PushType). */
enum class PushType {
    FRIEND_REQUEST,
    FRIEND_ACCEPTED,
    COMMENT_LIKED,
    MOVIE_SHARED,
    WATCHLIST_IN_CINEMA;

    companion object {
        fun fromWire(value: String?): PushType? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Lokalna podešavanja obaveštenja: koje vrste korisnik želi da vidi, i da li je već pitan za
 * sistemsku dozvolu. Isključena vrsta se ne prikazuje (backend je i dalje šalje i čuva u istoriji).
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(PushType.entries.associateWith { isEnabled(it) })
    val enabled: StateFlow<Map<PushType, Boolean>> = _enabled.asStateFlow()

    fun isEnabled(type: PushType): Boolean = prefs.getBoolean(key(type), true)

    fun setEnabled(type: PushType, enabled: Boolean) {
        prefs.edit { putBoolean(key(type), enabled) }
        _enabled.value = _enabled.value + (type to enabled)
    }

    /** Da li je korisniku već prikazano objašnjenje + sistemski zahtev za dozvolu (Android 13+). */
    var permissionPromptShown: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_PROMPT_SHOWN, value) }

    private fun key(type: PushType) = "enabled_${type.name}"

    private companion object {
        const val KEY_PERMISSION_PROMPT_SHOWN = "permission_prompt_shown"
    }
}
