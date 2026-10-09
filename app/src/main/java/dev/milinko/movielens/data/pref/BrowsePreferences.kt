package dev.milinko.movielens.data.pref

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** How movies are shown on the "See all" screens (Suggested for you / In cinemas). */
enum class BrowseViewMode { GRID, LIST }

/**
 * Remembers the grid/list choice on the "See all" screens: one choice shared by both screens,
 * stored locally on the device (same pattern as ThemePreferences).
 */
@Singleton
class BrowsePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("browse_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_VIEW_MODE = "view_mode"
    }

    private val _viewMode = MutableStateFlow(
        BrowseViewMode.entries.firstOrNull { it.name == prefs.getString(KEY_VIEW_MODE, null) }
            ?: BrowseViewMode.GRID
    )
    val viewMode: StateFlow<BrowseViewMode> = _viewMode.asStateFlow()

    /** Switches between grid and list. */
    fun toggleViewMode() {
        val next = if (_viewMode.value == BrowseViewMode.GRID) BrowseViewMode.LIST else BrowseViewMode.GRID
        prefs.edit { putString(KEY_VIEW_MODE, next.name) }
        _viewMode.value = next
    }
}
