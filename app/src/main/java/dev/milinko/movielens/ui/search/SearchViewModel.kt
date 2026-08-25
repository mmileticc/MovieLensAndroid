package dev.milinko.movielens.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.RecentSearchesStore
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val searchQuery: String = "",
    // Odvojeno od isAnalyzingImage - deljeno "isLoading" polje je značilo da kucanje teksta dok je
    // slika izabrana zna da prikaže "Analiziram sliku..." dugme iako se ne dešava nikakva analiza.
    val isSearching: Boolean = false,
    val isAnalyzingImage: Boolean = false,
    val selectedImageUri: String? = null,
    val searchResults: List<MovieSearchResult> = emptyList(),
    val visionResult: VisionSearchResponse? = null,
    val error: String? = null,
    // Faza D (17. avgust 2026, predlog mentora Marka) - "U bioskopima" mod: semantička pretraga
    // svedena na filmove trenutno na repertoaru (videti backend CinemaSemanticIndex). Odvojeno od
    // searchResults/isSearching iznad - drugačiji tip podataka (NowInCinemasMovie, ne
    // MovieSearchResult) i drugačiji trigger (samo eksplicitan submit, nikad debounce - videti
    // setSearchQuery/searchMovies ispod).
    val isCinemaMode: Boolean = false,
    val cinemaResults: List<NowInCinemasMovie> = emptyList(),
    val isSearchingCinemas: Boolean = false,
    // Faza D dorada (korisnički zahtev za poređenje Gemini vs. lokalni MiniLM embedding) - bira koji
    // embedding provajder backend koristi za "U bioskopima" semantičku pretragu. Podrazumevano
    // Gemini (isti kvalitet kao pre ove dorade). Vidljivo samo u isCinemaMode - videti SearchScreen.
    val useLocalEmbedding: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val visionRepository: VisionRepository,
    private val recentSearchesStore: RecentSearchesStore,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Dorada (17. avgust 2026, korisničko pitanje "je l keš vezan za telefon a ne za nalog") -
    // istorija MORA biti po nalogu (videti opširno obrazloženje u RecentSearchesStore.kt), ne
    // deljena za sve naloge na istom telefonu. Scope se čita JEDNOM ovde - dovoljno, jer
    // NavGraph.onSignOut čisti ceo back stack (popUpTo(0){inclusive=true}) pa se posle promene
    // naloga ionako pravi NOVA instanca ovog ViewModel-a kad se korisnik vrati na Search. "guest"
    // bucket za neulogovanog korisnika - tekstualna pretraga radi i bez prijave.
    private val recentSearchesScopeId = sessionManager.getUserId() ?: "guest"

    // Dorada (17. avgust 2026, korisnička žalba "prazno dok se ne krene sa pretragom") - odvojeno
    // od SearchUiState (isti obrazac kao sessionUser/baseUrl u ProfileViewModel) jer ne zavisi ni
    // od jednog mrežnog poziva, samo lokalno čita/piše SharedPreferences.
    private val _recentSearches = MutableStateFlow(recentSearchesStore.getQueries(recentSearchesScopeId))
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    // Debounce posao za pretragu dok korisnik kuca - svaki novi karakter otkazuje prethodni.
    private var searchJob: Job? = null

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        // Faza D - u "U bioskopima" modu NEMA debounce/"live" pretragu - svaki upit je pravi API
        // poziv koji embeduje tekst preko Gemini-ja (stvaran trošak), pa se traži samo na
        // eksplicitan submit (videti searchMovies ispod). Tekst polja se i dalje ažurira iznad.
        if (_uiState.value.isCinemaMode) return

        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), error = null)
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(trimmed)
        }
    }

    /**
     * Faza D (17. avgust 2026, predlog mentora Marka) - prebacuje između obične tekstualne
     * pretrage (ceo TMDB katalog) i semantičke pretrage svedene na filmove trenutno na repertoaru
     * bioskopa. Čisti rezultate OBA moda pri prebacivanju - sprečava da se stari rezultati
     * drugačijeg tipa/obima vide ispod novog moda dok korisnik ne pretraži ponovo.
     */
    fun setCinemaMode(enabled: Boolean) {
        if (_uiState.value.isCinemaMode == enabled) return
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isCinemaMode = enabled,
            searchResults = emptyList(),
            cinemaResults = emptyList(),
            visionResult = null,
            error = null,
            // Pretraga slikom ne postoji u "U bioskopima" modu (dugme za kameru se onemogućava u
            // SearchScreen-u) - ako je korisnik imao sliku izabranu PRE prebacivanja, uklanja se
            // ovde da submit dugme posle prebacivanja ne bi tiho pokrenulo vision analizu umesto
            // semantičke pretrage.
            selectedImageUri = if (enabled) null else _uiState.value.selectedImageUri
        )
    }

    /**
     * Trenutna pretraga na Enter/IME search akciju - preskače debounce, traži odmah. Namerno
     * upisuje u istoriju (eksplicitna, namerna akcija - videti napomenu na [recordCurrentQueryInHistory]).
     * U "U bioskopima" modu delegira na [searchCinemasSemantic] - istorija pretraga ostaje samo za
     * tekstualni mod (videti MOVIELENS_DESIGN_PLAN.md Faza D).
     */
    fun searchMovies() {
        if (_uiState.value.isCinemaMode) {
            searchCinemasSemantic()
            return
        }

        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        _recentSearches.value = recentSearchesStore.addQuery(recentSearchesScopeId, query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    /**
     * Faza D dorada - prebacuje embedding provajder za "U bioskopima" pretragu (Gemini/lokalni
     * MiniLM), radi poređenja ponašanja. Čisti stare rezultate - drugi provajder znači drugi
     * vektorski prostor, stari rezultati više nisu relevantni dok se ne pretraži ponovo.
     */
    fun setUseLocalEmbedding(enabled: Boolean) {
        if (_uiState.value.useLocalEmbedding == enabled) return
        _uiState.value = _uiState.value.copy(useLocalEmbedding = enabled, cinemaResults = emptyList())
    }

    private fun searchCinemasSemantic() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        val engine = if (_uiState.value.useLocalEmbedding) "local" else "gemini"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingCinemas = true, error = null)
            movieRepository.semanticSearchNowInCinemas(query, engine)
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingCinemas = false,
                        cinemaResults = results
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSearchingCinemas = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri pretrazi")
                    )
                }
        }
    }

    /** Ponovo pokreće pretragu za upit iz istorije (tap na chip) - preskače debounce, kao Enter. */
    fun selectRecentQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        _recentSearches.value = recentSearchesStore.addQuery(recentSearchesScopeId, query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    fun removeRecentQuery(query: String) {
        _recentSearches.value = recentSearchesStore.removeQuery(recentSearchesScopeId, query)
    }

    /**
     * Dorada #2 (17. avgust 2026, korisnička žalba "i uz debounce prečesto čuva") - prvi pokušaj
     * (upis na SVAKI performSearch, uklj. debounced "live" pretragu dok korisnik kuca) se pokazao
     * prečest - svaka pauza u kucanju duža od 300ms je već upisivala upit, čak i ako korisnik samo
     * pregleda rezultate uživo i ništa ne otvori. Vraćeno na eksplicitne akcije (searchMovies/
     * selectRecentQuery iznad), plus OVA funkcija - poziva je SearchScreen kad korisnik stvarno
     * klikne na neki rezultat (poster/red) i ode na Movie Detail, jasan signal da je pretraga
     * "uspela" i vredi je zapamtiti. Ne-op ako je upit prazan (npr. čist vision/slika pretraga).
     */
    fun recordCurrentQueryInHistory() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isNotEmpty()) {
            _recentSearches.value = recentSearchesStore.addQuery(recentSearchesScopeId, query)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        movieRepository.searchMovies(query)
            .onSuccess { results ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = results
                )
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri pretrazi")
                )
            }
    }

    fun onImageSelected(uri: String?) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            visionResult = null,
            error = null
        )
    }

    fun detectImage() {
        val imageUri = _uiState.value.selectedImageUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzingImage = true, error = null)
            try {
                val response = visionRepository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    visionResult = response,
                    selectedImageUri = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri analizi slike")
                )
            }
        }
    }

    fun clearResults() {
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            visionResult = null,
            searchQuery = "",
            error = null,
            cinemaResults = emptyList()
        )
    }
}

