package dev.milinko.movielens.ui.library

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sortiranje taba "Za gledanje". */
enum class WatchlistSort { ADDED, TMDB_RATING, TITLE, YEAR }

/** Sortiranje taba "Odgledano". */
enum class HistorySort { WATCHED, MY_RATING, TMDB_RATING, TITLE }

/** Sirovo stanje - liste iz Room keša + filteri. UI dobija već filtriranu/sortiranu verziju. */
private data class LibraryRawState(
    val watchlist: List<WatchlistItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    // Stavke uklonjene sa liste za gledanje koje čekaju da istekne "Poništi" snackbar - sakrivene
    // iz prikaza, ali se mrežni poziv šalje tek kad korisnik ne poništi (videti requestRemove).
    val pendingRemovalIds: Set<Int> = emptySet(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val watchlistSort: WatchlistSort = WatchlistSort.ADDED,
    val historySort: HistorySort = HistorySort.WATCHED,
    // Minimalna moja ocena na tabu "Odgledano" (npr. 8 = samo filmovi koje sam ocenio 8+); null = svi.
    val minMyRating: Int? = null,
    val isLoading: Boolean = false,
    val requiresSignIn: Boolean = false,
    val error: String? = null
)

data class LibraryUiState(
    val watchlist: List<WatchlistItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    // Ukupan broj pre filtera - razlikuje "lista je prazna" od "filter ništa ne pogađa".
    val watchlistTotal: Int = 0,
    val historyTotal: Int = 0,
    val availableGenres: List<Genre> = emptyList(),
    val query: String = "",
    val selectedGenreIds: Set<Int> = emptySet(),
    val watchlistSort: WatchlistSort = WatchlistSort.ADDED,
    val historySort: HistorySort = HistorySort.WATCHED,
    val minMyRating: Int? = null,
    // Samo dok NI lokalni keš NI mreža još nisu vratili ništa (prvi ikad ulazak, prazna baza).
    val isLoading: Boolean = false,
    val requiresSignIn: Boolean = false,
    val error: String? = null
) {
    val hasActiveFilters: Boolean get() = query.isNotBlank() || selectedGenreIds.isNotEmpty() || minMyRating != null
}

sealed class LibraryEvent {
    /** Prikaži "Uklonjeno · Poništi" snackbar za ovu stavku. */
    data class WatchlistItemRemoved(val tmdbId: Int, val title: String) : LibraryEvent()
    data class Message(@StringRes val messageRes: Int) : LibraryEvent()
}

/**
 * Offline-first: Room (preko UserRepository.observeWatchlist/observeHistory) je izvor istine za
 * UI - čita se instant, radi i bez interneta. Mreža (getWatchlist/getHistory) samo osvežava Room
 * keš u pozadini; čim se upiše, Flow automatski gurne novu listu u stanje. Videti
 * MOVIELENS_ROADMAP.md za obrazloženje offline obima (samo watchlist/istorija, ne i pretraga).
 *
 * UX dorada (oktobar 2026): lokalni filteri (naziv, žanr, minimalna moja ocena) i sortiranje po
 * tabu, "Poništi" za uklanjanje sa liste, potvrda/greška za izmenu ocene, i greška učitavanja koja
 * se stvarno prikazuje (ranije je pad mreže izgledao kao prazna lista).
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val raw = MutableStateFlow(LibraryRawState())

    val uiState: StateFlow<LibraryUiState> = raw
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private var observingForUserId: String? = null
    private var watchlistCollectJob: Job? = null
    private var historyCollectJob: Job? = null

    // Bugfix (E1.2, 2. septembar 2026, feedback testera - "greske 500/400 pri brzim uzastopnim
    // klikovima") - isti obrazac kao MovieDetailViewModel (historyJob/watchlistJob), samo keyed po
    // tmdbId jer ovde više različitih filmova u listi mogu da se menjaju nezavisno (ne sme jedan
    // klik da otkaže zahtev za DRUGI film). Nova akcija nad ISTIM filmom otkaže prethodnu ako je
    // još u letu, umesto da se dva zahteva sudare na backendu.
    private val historyJobs = mutableMapOf<Int, Job>()
    private val removalJobs = mutableMapOf<Int, Job>()

    /** Učitava biblioteku za trenutno ulogovanog korisnika (iz SessionManager-a). */
    fun loadLibrary() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            stopObservingLocalCache()
            observingForUserId = null
            raw.value = LibraryRawState(requiresSignIn = true)
            return
        }

        if (observingForUserId != userId) {
            observingForUserId = userId
            raw.value = LibraryRawState(requiresSignIn = false)
            startObservingLocalCache(userId)
        }

        refreshFromNetwork(userId)
    }

    /** "Pokušaj ponovo" na kartici greške. */
    fun retry() = loadLibrary()

    private fun startObservingLocalCache(userId: String) {
        stopObservingLocalCache()
        watchlistCollectJob = viewModelScope.launch {
            userRepository.observeWatchlist(userId).collect { watchlist ->
                raw.update { it.copy(watchlist = watchlist) }
            }
        }
        historyCollectJob = viewModelScope.launch {
            userRepository.observeHistory(userId).collect { history ->
                raw.update { it.copy(history = history) }
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
        val hasNothingToShow = raw.value.watchlist.isEmpty() && raw.value.history.isEmpty()

        viewModelScope.launch {
            raw.update { it.copy(isLoading = hasNothingToShow, error = null) }

            // Watchlist i istorija su nezavisni pozivi - paralelno umesto sekvencijalno
            // (prepolovljuje vreme čekanja na spor/udaljen backend). Rezultat se ne piše ovde u
            // stanje direktno - to radi Room Flow collector iznad, čim getWatchlist/getHistory
            // upišu svež keš. Ovde samo gasimo spinner i javljamo grešku.
            val watchlistDeferred = async { userRepository.getWatchlist(userId) }
            val historyDeferred = async { userRepository.getHistory(userId) }
            val watchlistResult = watchlistDeferred.await()
            val historyResult = historyDeferred.await()

            val firstException = watchlistResult.exceptionOrNull() ?: historyResult.exceptionOrNull()
            raw.update {
                it.copy(
                    isLoading = false,
                    // Greška se prikazuje UVEK kad poziv padne (ranije samo kad nije bilo keša, a ni
                    // tad je ekran nije čitao). Kad keš postoji, lista ostaje vidljiva ispod poruke -
                    // korisnik zna da gleda možda zastarele podatke i može da pokuša ponovo.
                    error = firstException?.let { e ->
                        dev.milinko.movielens.util.friendlyMessage(e, "Biblioteka trenutno ne može da se osveži")
                    }
                )
            }
        }
    }

    // --- Filteri i sortiranje (lokalno, bez mreže) ---

    fun onQueryChange(query: String) = raw.update { it.copy(query = query) }

    fun onToggleGenre(genreId: Int) = raw.update {
        val selected = it.selectedGenreIds
        it.copy(selectedGenreIds = if (genreId in selected) selected - genreId else selected + genreId)
    }

    fun onWatchlistSortChange(sort: WatchlistSort) = raw.update { it.copy(watchlistSort = sort) }

    fun onHistorySortChange(sort: HistorySort) = raw.update { it.copy(historySort = sort) }

    /** Ponovni klik na isti prag ga isključuje. */
    fun onMinMyRatingChange(minRating: Int?) = raw.update {
        it.copy(minMyRating = if (it.minMyRating == minRating) null else minRating)
    }

    // --- Izmena ocene ---

    /**
     * Izmena ocene direktno iz taba Odgledano (bez odlaska na Movie Detail) - optimistički, isti
     * obrazac kao MovieDetailViewModel.rateMovie. Film je ovde po definiciji već odgledan, pa uvek
     * ide PUT (updateHistoryItem), nikad POST.
     */
    fun rateMovie(tmdbId: Int, rating: Int) {
        val userId = sessionManager.getUserId() ?: return
        val previousHistory = raw.value.history
        raw.update { state ->
            state.copy(history = state.history.map { if (it.tmdbId == tmdbId) it.copy(userRating = rating.toDouble()) else it })
        }

        historyJobs[tmdbId]?.cancel()
        historyJobs[tmdbId] = viewModelScope.launch {
            userRepository.updateHistoryItem(userId, tmdbId, rating.toDouble())
                .onSuccess { _events.tryEmit(LibraryEvent.Message(R.string.library_rating_saved)) }
                .onFailure { e ->
                    Log.w("LibraryViewModel", "rateMovie nije uspeo: ${e.message}")
                    raw.update { it.copy(history = previousHistory) }
                    _events.tryEmit(LibraryEvent.Message(R.string.library_rating_failed))
                }
        }
    }

    // --- Uklanjanje sa liste za gledanje, sa "Poništi" ---

    /**
     * Stavka odmah nestaje iz prikaza i ekran pokazuje "Uklonjeno · Poništi". Mrežni poziv se šalje
     * tek u [commitRemove] (kad snackbar istekne ili korisnik napusti ekran) - "Poništi" zato ne
     * mora ništa da vraća na serveru, samo ponovo prikaže stavku.
     */
    fun requestRemove(item: WatchlistItem) {
        raw.update { it.copy(pendingRemovalIds = it.pendingRemovalIds + item.tmdbId) }
        _events.tryEmit(LibraryEvent.WatchlistItemRemoved(item.tmdbId, item.title))
    }

    fun undoRemove(tmdbId: Int) {
        if (removalJobs.containsKey(tmdbId)) return // mrežni poziv je već krenuo
        raw.update { it.copy(pendingRemovalIds = it.pendingRemovalIds - tmdbId) }
    }

    fun commitRemove(tmdbId: Int) {
        val userId = sessionManager.getUserId() ?: return
        if (tmdbId !in raw.value.pendingRemovalIds || removalJobs.containsKey(tmdbId)) return

        removalJobs[tmdbId] = viewModelScope.launch {
            userRepository.removeFromWatchlist(userId, tmdbId)
                .onFailure { e ->
                    Log.w("LibraryViewModel", "removeFromWatchlist nije uspeo: ${e.message}")
                    _events.tryEmit(LibraryEvent.Message(R.string.library_remove_failed))
                }
            // Uspeh: repozitorijum je obrisao red iz Room-a, Flow ga više ne emituje. Neuspeh:
            // stavka se vraća u prikaz (gore je korisnik obavešten porukom).
            raw.update { it.copy(pendingRemovalIds = it.pendingRemovalIds - tmdbId) }
            removalJobs.remove(tmdbId)
        }
    }

    /** Korisnik napušta ekran pre isteka snackbar-a - uklanjanje koje nije poništeno se izvršava. */
    fun commitAllPendingRemovals() {
        raw.value.pendingRemovalIds.forEach { commitRemove(it) }
    }

    // --- Izvođenje UI stanja ---

    private fun LibraryRawState.toUiState(): LibraryUiState {
        val q = query.trim()
        fun matches(title: String, genres: List<Genre>?): Boolean =
            (q.isEmpty() || title.contains(q, ignoreCase = true)) &&
                (selectedGenreIds.isEmpty() || genres.orEmpty().any { it.id in selectedGenreIds })

        val visibleWatchlist = watchlist.filter { it.tmdbId !in pendingRemovalIds }
        val filteredWatchlist = visibleWatchlist
            .filter { matches(it.title, it.genres) }
            .let { list ->
                when (watchlistSort) {
                    WatchlistSort.ADDED -> list.sortedByDescending { it.addedAt }
                    WatchlistSort.TMDB_RATING -> list.sortedByDescending { it.voteAverage ?: -1.0 }
                    WatchlistSort.TITLE -> list.sortedBy { it.title.lowercase() }
                    WatchlistSort.YEAR -> list.sortedByDescending { it.releaseYear ?: Int.MIN_VALUE }
                }
            }

        val filteredHistory = history
            .filter { matches(it.title, it.genres) }
            .filter { item -> minMyRating == null || item.userRating >= minMyRating }
            .let { list ->
                when (historySort) {
                    HistorySort.WATCHED -> list.sortedByDescending { it.watchedAt }
                    HistorySort.MY_RATING -> list.sortedByDescending { it.userRating }
                    HistorySort.TMDB_RATING -> list.sortedByDescending { it.voteAverage ?: -1.0 }
                    HistorySort.TITLE -> list.sortedBy { it.title.lowercase() }
                }
            }

        val availableGenres = (visibleWatchlist.flatMap { it.genres.orEmpty() } + history.flatMap { it.genres.orEmpty() })
            .distinctBy { it.id }
            .sortedBy { it.name }

        return LibraryUiState(
            watchlist = filteredWatchlist,
            history = filteredHistory,
            watchlistTotal = visibleWatchlist.size,
            historyTotal = history.size,
            availableGenres = availableGenres,
            query = query,
            selectedGenreIds = selectedGenreIds,
            watchlistSort = watchlistSort,
            historySort = historySort,
            minMyRating = minMyRating,
            isLoading = isLoading,
            requiresSignIn = requiresSignIn,
            error = error
        )
    }
}
