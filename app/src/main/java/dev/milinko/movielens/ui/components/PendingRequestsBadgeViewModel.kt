package dev.milinko.movielens.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Broj dolaznih zahteva za prijateljstvo za bedž na tabu "Profil" u donjoj navigaciji (UX dorada,
 * oktobar 2026 - ranije se novi zahtev video tek kad korisnik sam otvori Profil i skroluje).
 *
 * Čita isti Room keš kao Profile ekran (`observeCachedPendingRequests`), pa se bedž ažurira čim
 * Profile ili ovaj ViewModel osveže keš sa mreže. Mrežno osvežavanje je ograničeno na jednom u
 * [REFRESH_INTERVAL_MS] za ceo proces - svaki ekran sa donjom navigacijom ima svoju instancu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PendingRequestsBadgeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    // Korisnik se može prijaviti/odjaviti dok je ekran (i ovaj ViewModel) živ - zato se ID ponovo
    // čita u refresh(), a broj prati aktuelnog korisnika.
    private val userId = MutableStateFlow(sessionManager.getUserId())

    val pendingCount: StateFlow<Int> = userId
        .flatMapLatest { id ->
            if (id == null) flowOf(0) else userRepository.observeCachedPendingRequests(id).map { it.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Poziva se svaki put kad se donja navigacija prikaže (ulazak na ekran). */
    fun refresh() {
        val id = sessionManager.getUserId()
        userId.value = id
        if (id == null) return
        val now = System.currentTimeMillis()
        if (now - lastNetworkRefreshAt < REFRESH_INTERVAL_MS) return
        lastNetworkRefreshAt = now
        viewModelScope.launch {
            // Upisuje svež spisak u isti keš - pendingCount se sam ažurira. Greška je nebitna za bedž.
            userRepository.getPendingFriendRequests(id)
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 60_000L

        @Volatile
        var lastNetworkRefreshAt = 0L
    }
}
