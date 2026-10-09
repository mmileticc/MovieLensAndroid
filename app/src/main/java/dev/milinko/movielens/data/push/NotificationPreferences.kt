package dev.milinko.movielens.data.push

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Notification types - the backend sends the same names in the `type` field (PushService.PushType). */
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
 * Local notification settings: which types the user wants to see and whether they have already been
 * asked for the system permission. A disabled type is not shown (the backend still sends it and
 * keeps it in the history).
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(PushType.entries.associateWith { isEnabled(it) })
    val enabled: StateFlow<Map<PushType, Boolean>> = _enabled.asStateFlow()

    /** Whether notifications of [type] are shown (enabled by default). */
    fun isEnabled(type: PushType): Boolean = prefs.getBoolean(key(type), true)

    /** Enables or disables notifications of [type]. */
    fun setEnabled(type: PushType, enabled: Boolean) {
        prefs.edit { putBoolean(key(type), enabled) }
        _enabled.value = _enabled.value + (type to enabled)
    }

    /** Whether the explanation and the system permission request (Android 13+) were already shown. */
    var permissionPromptShown: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_PROMPT_SHOWN, value) }

    private fun key(type: PushType) = "enabled_${type.name}"

    private companion object {
        const val KEY_PERMISSION_PROMPT_SHOWN = "permission_prompt_shown"
    }
}
