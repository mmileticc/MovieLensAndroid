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
 * Faza 2 ("Tamna tema" da stvarno radi, MOVIELENS_DESIGN_PLAN.md). Ranije je "Tamna tema" prekidač
 * bio ožičen na backend GET/PUT /api/users/{id}/settings - koja ruta na backendu NIKAD nije
 * postojala (potvrđeno pretragom celog backend koda, nema settings tabele/rute), pa je
 * `ProfileUiState.userSettings` uvek bio null i CEO "Postavke aplikacije" odeljak (uklj. Verzija i
 * Backend URL kartice, ne samo prekidač) se nikad nije ni renderovao - korisnik ga nikad nije video.
 *
 * Tema je inače prirodnije čisto lokalno/uređaj-podešavanje (kao u većini appova), ne nešto što se
 * sinhronizuje kroz nalog, pa ovde čuvamo samo lokalno u SharedPreferences (isti obrazac kao
 * SessionManager/UrlStorage) - nezavisno od mreže/logina, radi i za neulogovanog korisnika.
 *
 * Podrazumevano tamna (true) da postojeći korisnici ne vide iznenadnu promenu dok je svesno ne
 * uključe/isključe.
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

    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE, enabled) }
        _isDarkMode.value = enabled
    }

    fun toggleDarkMode() {
        setDarkMode(!_isDarkMode.value)
    }
}
