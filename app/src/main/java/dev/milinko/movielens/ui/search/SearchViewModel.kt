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
    // Dorada (2. septembar 2026, korisnički zahtev - "poruka o preopterećenju me para oči, deluje
    // kao greška koju sam ja skrivio") - razlikuje "pravu" grešku (crveni karton) od privremenog,
    // ne-korisnikovog stanja poput Gemini overload-a (smireniji, info karton). Videti
    // TransientServiceException.kt i SearchScreen.kt (uslovni stil error kartona).
    val isSoftError: Boolean = false,
    // Faza D (17. avgust 2026, predlog mentora Marka) - "U bioskopima" mod: semantička pretraga
    // svedena na filmove trenutno na repertoaru (videti backend CinemaSemanticIndex). Odvojeno od
    // searchResults/isSearching iznad - drugačiji tip podataka (NowInCinemasMovie, ne
    // MovieSearchResult).
    // E2.1/E2.4 dorada (2. septembar 2026, korisnički zahtev - "dok se kuca da radi live pretraga
    // po nazivu kao obična pretraga, a semantička ide samo na klik lupice") - `cinemaResults` se
    // sad ažurira na DVA načina: (1) debounced lokalni filter po nazivu nad `allCinemaMovies` dok
    // korisnik kuca (bez mreže, besplatno - videti setSearchQuery), (2) pravi semantički poziv
    // SAMO na eksplicitan submit (nepromenjeno, videti searchCinemasSemantic/searchMovies).
    val isCinemaMode: Boolean = false,
    val cinemaResults: List<NowInCinemasMovie> = emptyList(),
    val isSearchingCinemas: Boolean = false,
    // Bugfix (2. septembar 2026) - dok debounce (300ms) čeka pre lokalnog filtriranja, `cinemaResults`
    // je i dalje "stara" (prazna posle svakog karaktera) - bez ovog flag-a, "nema poklapanja" poruka
    // (SearchScreen) bi treperila na SVAKI karakter pre nego što filter stigne da se izvrši. Isti
    // princip kao isSearchingCinemas, samo za jeftiniji lokalni posao umesto mrežnog poziva.
    val isFilteringCinemasLive: Boolean = false,
    // Cela lista filmova trenutno na repertoaru, preuzeta JEDNOM (network poziv bez ikakvog
    // embedding troška - isti podaci kao Home "U bioskopima sada" traka) čim se uđe u "U
    // bioskopima" mod - lokalni filter iznad radi nad ovom listom, ne nad mrežom.
    val allCinemaMovies: List<NowInCinemasMovie> = emptyList(),
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

        // E2.1/E2.4 dorada (2. septembar 2026) - RANIJE u "U bioskopima" modu NIJE bilo
        // debounce/"live" pretrage jer je svaki upit slao pravi API poziv koji embeduje tekst
        // preko Gemini-ja (stvaran trošak) - delovalo je "mrtvo" dok korisnik kuca. Sad se dok
        // kuca radi ISTI princip kao obična pretraga (debounce + filter), samo lokalno nad već
        // preuzetom listom `allCinemaMovies` (videti loadCinemaCatalog) - bez mreže, besplatno.
        // Pravi semantički poziv i dalje ide SAMO na eksplicitan submit (searchMovies ispod),
        // nepromenjeno.
        if (_uiState.value.isCinemaMode) {
            filterCinemaResultsLive(query)
            return
        }

        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), error = null, isSoftError = false)
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(trimmed)
        }
    }

    /**
     * E2.1/E2.4 dorada - lokalni debounced filter po nazivu nad `allCinemaMovies` (bez mreže).
     * Isti debounce (300ms) i minimalna dužina kao obična tekstualna pretraga - korisniku deluje
     * identično dok kuca, razlika (lokalni filter vs. Gemini embedding) je nevidljiva.
     */
    private fun filterCinemaResultsLive(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(cinemaResults = emptyList(), isFilteringCinemasLive = false, error = null, isSoftError = false)
            return
        }
        _uiState.value = _uiState.value.copy(isFilteringCinemasLive = true)
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val filtered = _uiState.value.allCinemaMovies.filter { it.title.contains(trimmed, ignoreCase = true) }
            _uiState.value = _uiState.value.copy(cinemaResults = filtered, isFilteringCinemasLive = false, error = null, isSoftError = false)
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
            // Higijena - searchJob?.cancel() iznad prekida debounce posao PRE nego što stigne da
            // ovaj flag vrati na false, pa bi bez ovoga mogao ostati "zaglavljen" na true ako se
            // mod promeni usred debounce prozora.
            isFilteringCinemasLive = false,
            visionResult = null,
            error = null,
            isSoftError = false,
            // Pretraga slikom ne postoji u "U bioskopima" modu (dugme za kameru se onemogućava u
            // SearchScreen-u) - ako je korisnik imao sliku izabranu PRE prebacivanja, uklanja se
            // ovde da submit dugme posle prebacivanja ne bi tiho pokrenulo vision analizu umesto
            // semantičke pretrage.
            selectedImageUri = if (enabled) null else _uiState.value.selectedImageUri
        )
        // E2.1/E2.4 dorada - katalog se preuzima JEDNOM po ulasku u mod (ne na svaki karakter),
        // da lokalni filter u filterCinemaResultsLive ima nad čime da radi. Ako je već preuzet
        // (npr. korisnik izašao pa se vratio u istom ViewModel-u), ne ponavlja se poziv.
        if (enabled && _uiState.value.allCinemaMovies.isEmpty()) {
            loadCinemaCatalog()
        }
    }

    private var cinemaCatalogJob: Job? = null

    /**
     * E2.1/E2.4 dorada - preuzima celu listu filmova trenutno na repertoaru (isti podaci kao Home
     * "U bioskopima sada" traka, obična ruta bez embedding troška) da lokalni filter po nazivu
     * ima nad čime da radi dok korisnik kuca. Tiho guta grešku - ako padne, lokalni filter samo
     * neće naći ništa (prazna lista), a eksplicitna semantička pretraga (submit) i dalje radi
     * potpuno nezavisno, svojim network pozivom.
     */
    private fun loadCinemaCatalog() {
        cinemaCatalogJob?.cancel()
        cinemaCatalogJob = viewModelScope.launch {
            movieRepository.getNowInCinemas().onSuccess { movies ->
                _uiState.value = _uiState.value.copy(allCinemaMovies = movies)
                // Ako je korisnik već nešto ukucao dok se katalog učitavao, primeni filter odmah
                // umesto da čeka sledeći karakter.
                val query = _uiState.value.searchQuery.trim()
                if (query.length >= MIN_QUERY_LENGTH) {
                    _uiState.value = _uiState.value.copy(
                        cinemaResults = movies.filter { it.title.contains(query, ignoreCase = true) }
                    )
                }
            }
        }
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
            _uiState.value = _uiState.value.copy(isSearchingCinemas = true, error = null, isSoftError = false)
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
                        error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri pretrazi"),
                        isSoftError = false
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
        _uiState.value = _uiState.value.copy(isSearching = true, error = null, isSoftError = false)
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
                    error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri pretrazi"),
                    isSoftError = false
                )
            }
    }

    fun onImageSelected(uri: String?) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            visionResult = null,
            error = null,
            isSoftError = false
        )
    }

    fun detectImage() {
        val imageUri = _uiState.value.selectedImageUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAnalyzingImage = true, error = null, isSoftError = false)
            try {
                val response = visionRepository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    visionResult = response,
                    selectedImageUri = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: dev.milinko.movielens.util.TransientServiceException) {
                // Videti TransientServiceException.kt/VisionRepository.kt - Gemini overload i
                // slična "nije tvoja greška" stanja dobijaju smireniji UI umesto crvene "Greške".
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    error = e.message,
                    isSoftError = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri analizi slike"),
                    isSoftError = false
                )
            }
        }
    }

    fun clearResults() {
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            visionResult = null,
            searchQuery = "",
            error = null,
            isSoftError = false,
            cinemaResults = emptyList(),
            isFilteringCinemasLive = false
        )
    }
}

