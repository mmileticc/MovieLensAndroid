package dev.milinko.movielens.data.pref

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dark/light theme, stored locally on the device (like most apps, not synced with the account),
 * so it also works signed out and offline. Dark by default.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_DARK_MODE = "dark_mode"
    }

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    /** Sets and stores the theme. */
    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE, enabled) }
        _isDarkMode.value = enabled
    }

    /** Switches between dark and light. */
    fun toggleDarkMode() {
        setDarkMode(!_isDarkMode.value)
    }
}
