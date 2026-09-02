package dev.milinko.movielens.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val watchlist: List<WatchlistItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    // Samo dok NI lokalni keš NI mreža još nisu vratili ništa (prvi ikad ulazak, prazna baza).
    val isLoading: Boolean = false,
    val requiresSignIn: Boolean = false,
    val error: String? = null
)

/**
 * Offline-first: Room (preko UserRepository.observeWatchlist/observeHistory) je izvor istine za
 * UI - čita se instant, radi i bez interneta. Mreža (getWatchlist/getHistory) samo osvežava Room
 * keš u pozadini; čim se upiše, Flow automatski gurne novu listu u uiState. Videti
 * MOVIELENS_ROADMAP.md za obrazloženje offline obima (samo watchlist/istorija, ne i pretraga).
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var observingForUserId: String? = null
    private var watchlistCollectJob: Job? = null
    private var historyCollectJob: Job? = null

    // Bugfix (E1.2, 2. septembar 2026, feedback testera - "greske 500/400 pri brzim uzastopnim
    // klikovima") - isti obrazac kao MovieDetailViewModel (historyJob/watchlistJob), samo keyed po
    // tmdbId jer ovde više različitih filmova u listi mogu da se menjaju nezavisno (ne sme jedan
    // klik da otkaže zahtev za DRUGI film). Nova akcija nad ISTIM filmom otkaže prethodnu ako je
    // još u letu, umesto da se dva zahteva sudare na backendu.
    private val historyJobs = mutableMapOf<Int, Job>()
    private val watchlistJobs = mutableMapOf<Int, Job>()

    /** Učitava biblioteku za trenutno ulogovanog korisnika (iz SessionManager-a). */
    fun loadLibrary() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            stopObservingLocalCache()
            observingForUserId = null
            _uiState.value = LibraryUiState(requiresSignIn = true)
            return
        }

        if (observingForUserId != userId) {
            observingForUserId = userId
            _uiState.value = LibraryUiState(requiresSignIn = false)
            startObservingLocalCache(userId)
        }

        refreshFromNetwork(userId)
    }

    private fun startObservingLocalCache(userId: String) {
        stopObservingLocalCache()
        watchlistCollectJob = viewModelScope.launch {
            userRepository.observeWatchlist(userId).collect { watchlist ->
                _uiState.value = _uiState.value.copy(watchlist = watchlist)
            }
        }
        historyCollectJob = viewModelScope.launch {
            userRepository.observeHistory(userId).collect { history ->
                _uiState.value = _uiState.value.copy(history = history)
            }
        }
    }

    private fun stopObservingLocalCache() {
        watchlistCollectJob?.cancel()
        historyCollectJob?.cancel()
    }

    private fun refreshFromNetwork(userId: String) {
        // Spinner samo ako ekran nema baš ništa da pokaže dok čekamo mrežu (npr. prvi ikad ulazak,
        // Room prazan) - inače je osvežavanje tiho, korisnik već vidi keširanu listu.
        val hasNothingToShow = _uiState.value.watchlist.isEmpty() && _uiState.value.history.isEmpty()

        viewModelScope.launch {
            if (hasNothingToShow) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            // Watchlist i istorija su nezavisni pozivi - paralelno umesto sekvencijalno
            // (prepolovljuje vreme čekanja na spor/udaljen backend). Rezultat se ne piše ovde u
            // uiState.watchlist/history direktno - to radi Room Flow collector iznad, čim
            // getWatchlist/getHistory upišu svež keš. Ovde samo gasimo spinner i javljamo grešku.
            val watchlistDeferred = async { userRepository.getWatchlist(userId) }
            val historyDeferred = async { userRepository.getHistory(userId) }
            val watchlistResult = watchlistDeferred.await()
            val historyResult = historyDeferred.await()

            // Dorada (2. septembar 2026, korisnički zahtev - "nijedna sirova poruka ne sme da
            // postoji nigde") - RANIJE se `exceptionOrNull()?.message` uzimao direktno, bez
            // prevoda - repository sloj (UserRepository) je tada bacao generičke izuzetke poput
            // "Get watchlist failed: 500" koji bi se doslovno prikazali korisniku. Sad ide kroz
            // `friendlyMessage()`, isti obrazac kao svuda drugde u appu.
            val firstException = watchlistResult.exceptionOrNull() ?: historyResult.exceptionOrNull()
            val errorMessage = firstException?.let {
                dev.milinko.movielens.util.friendlyMessage(it, "Greška pri učitavanju biblioteke")
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                // Ako je poziv pao ALI već imamo keširane podatke da prikažemo (offline slučaj),
                // ne gušimo listu greškom preko cele stranice - samo tiho javimo (Toast na ekranu
                // bi bio previše nametljiv za "nema interneta, prikazujem keš" scenario za sada).
                error = if (hasNothingToShow) errorMessage else null
            )
        }
    }

    /**
     * Izmena ocene direktno iz Istorija taba (bez odlaska na Movie Detail) - optimistički,
     * isti obrazac kao MovieDetailViewModel.rateMovie. Film je ovde po definiciji već odgledan
     * (u istoriji je), pa uvek ide PUT (updateHistoryItem), nikad POST.
     */
    fun rateMovie(tmdbId: Int, rating: Int) {
        val userId = sessionManager.getUserId() ?: return
        val previousHistory = _uiState.value.history
        _uiState.value = _uiState.value.copy(
            history = previousHistory.map { if (it.tmdbId == tmdbId) it.copy(userRating = rating.toDouble()) else it }
        )

        historyJobs[tmdbId]?.cancel()
        historyJobs[tmdbId] = viewModelScope.launch {
            // Bugfix (E1.2) - tiho se loguje umesto da se prikaže kao greška preko cele Biblioteke -
            // najčešći uzrok je otkazan/sudaren zahtev od brzog uzastopnog klika na zvezdice.
            userRepository.updateHistoryItem(userId, tmdbId, rating.toDouble())
                .onFailure { e ->
                    Log.w("LibraryViewModel", "rateMovie nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                    _uiState.value = _uiState.value.copy(history = previousHistory)
                }
        }
    }

    /** Optimistički uklanja iz watchlist-e odmah (bez čekanja mreže), vraća stanje ako poziv ne uspe. */
    fun removeFromWatchlist(tmdbId: Int) {
        val userId = sessionManager.getUserId() ?: return
        val previousWatchlist = _uiState.value.watchlist
        _uiState.value = _uiState.value.copy(watchlist = previousWatchlist.filter { it.tmdbId != tmdbId })

        watchlistJobs[tmdbId]?.cancel()
        watchlistJobs[tmdbId] = viewModelScope.launch {
            // Bugfix (E1.2) - videti napomenu na rateMovie iznad, isti princip.
            userRepository.removeFromWatchlist(userId, tmdbId)
                .onFailure { e ->
                    Log.w("LibraryViewModel", "removeFromWatchlist nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                    _uiState.value = _uiState.value.copy(watchlist = previousWatchlist)
                }
        }
    }
}

