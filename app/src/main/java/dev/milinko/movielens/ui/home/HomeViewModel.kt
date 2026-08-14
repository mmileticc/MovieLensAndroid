package dev.milinko.movielens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.remote.movie.NowInCinemasMovie
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.UserRepository
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedImageUri: String? = null,
    val visionResult: VisionSearchResponse? = null,
    val trendingMovies: List<MovieSearchResult> = emptyList(),
    // Odvojeno od isLoading (koji je za vision detekciju) da ensureTrendingLoaded ne bude
    // slučajno blokiran dok korisnik analizira sliku, i obrnuto.
    val isTrendingLoading: Boolean = false,
    // Beskonačan skrol - odvojeno od isTrendingLoading (koji je samo za prvu stranu/spinner
    // preko cele liste) da se footer spinner prikazuje samo na dnu liste, ne preko svega.
    val isLoadingMore: Boolean = false,
    // false kad je poslednja strana vratila prazan rezultat - nema smisla dalje tražiti sledeću.
    val canLoadMore: Boolean = true,
    // "Predloženo za tebe" - lična personalizacija (Faza 4, taste profile po žanru), NAMERNO
    // odvojeno od trendingMovies (koji je uvek čist TMDB popular, bez obzira na login) - to su
    // dve različite stvari i obe treba da se vide, ne jedna umesto druge. Prazno ako korisnik
    // nije ulogovan (nema profil za personalizaciju).
    val personalizedMovies: List<MovieSearchResult> = emptyList(),
    // "U bioskopima sada" traka - bonus sadržaj, tiho ostaje prazna lista ako poziv padne (ne
    // blokira ni ne kvari ostatak Home ekrana).
    val nowInCinemas: List<NowInCinemasMovie> = emptyList(),
    // "Nastavi da gledaš" - iz Room keša (isti izvor kao Library, Faza 3.5), prazno ako korisnik
    // nije ulogovan ili nema ništa na watchlisti.
    val watchlistPreview: List<WatchlistItem> = emptyList(),
    // Swipe-down (pull-to-refresh) na Home ekranu - odvojeno od isTrendingLoading da ne prikazuje
    // duplirane spinnere (PullToRefreshBox ima sopstveni indikator preko ovog flag-a).
    val isRefreshing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userRepository: UserRepository,
    private val visionRepository: VisionRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Trenutna strana za trending/featured - ne ide u uiState jer UI-ju ne treba direktno,
    // samo loadMoreTrending() računa sledeću.
    private var currentPage = 1

    // Trenutna strana za "Predloženo za tebe" - swipe-down na Home-u rotira 1..MAX_PERSONALIZED_PAGE
    // pa se vrati na početak, isto kao "Osveži predloge" na Movie Night ekranu (MovieNightViewModel).
    private var personalizedPage = 1

    private companion object {
        const val MAX_PERSONALIZED_PAGE = 5
    }

    init {
        loadTrendingMovies()
        loadPersonalizedMovies()
        loadNowInCinemas()
        observeWatchlistPreview()
    }

    /**
     * Poziva se svaki put kad se Home ekran (ponovo) prikaže. HomeViewModel živi koliko i cela
     * sesija (Home je start destinacija), pa je init{} pozvan samo jednom - ako taj prvi poziv
     * ne uspe (npr. hladan start backend-a), lista bi ostala prazna zauvek bez ovoga. Ne dira
     * podatke ako već postoje ili ako je poziv u toku.
     */
    fun ensureTrendingLoaded() {
        if (_uiState.value.trendingMovies.isEmpty() && !_uiState.value.isTrendingLoading) {
            loadTrendingMovies()
        }
        if (sessionManager.getUserId() != null && _uiState.value.personalizedMovies.isEmpty()) {
            loadPersonalizedMovies()
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = visionRepository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    visionResult = response,
                    selectedImageUri = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Greška pri analizi slike"
                )
            }
        }
    }

    /**
     * "Popularni filmovi" - UVEK čist TMDB popular (ne personalizovano), bez obzira na login.
     * Personalizacija je odvojen red - videti loadPersonalizedMovies().
     */
    private fun loadTrendingMovies() {
        currentPage = 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTrendingLoading = true)
            movieRepository.getTrendingMovies(currentPage)
                .onSuccess { movies ->
                    _uiState.value = _uiState.value.copy(
                        isTrendingLoading = false,
                        trendingMovies = movies,
                        canLoadMore = movies.isNotEmpty()
                    )
                }
                .onFailure {
                    // Ne pišemo u `error` (to polje je vezano za vision UI) - samo gasimo loading
                    // flag da bi ensureTrendingLoaded() mogao da pokuša ponovo sledeći put kad se
                    // korisnik vrati na Home (npr. posle hladnog starta backend-a).
                    _uiState.value = _uiState.value.copy(isTrendingLoading = false)
                }
        }
    }

    /**
     * Beskonačan skrol - poziva se sa Home ekrana kad korisnik dođe blizu dna liste. Tiho ne radi
     * ništa ako je učitavanje već u toku (bilo prve strane ili prethodnog loadMore poziva) ili ako
     * je poslednja strana bila prazna (canLoadMore = false, verovatno kraj TMDB rezultata).
     */
    fun loadMoreTrending() {
        val state = _uiState.value
        if (state.isTrendingLoading || state.isLoadingMore || !state.canLoadMore) return

        val nextPage = currentPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            movieRepository.getTrendingMovies(nextPage)
                .onSuccess { movies ->
                    currentPage = nextPage
                    // Dedupe po tmdbId - TMDB stranice se teorijski mogu preklopiti ako se
                    // popularity poredak filmova promeni između dva poziva.
                    val existingIds = _uiState.value.trendingMovies.map { it.tmdbId }.toSet()
                    val newMovies = movies.filter { it.tmdbId !in existingIds }
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        trendingMovies = _uiState.value.trendingMovies + newMovies,
                        canLoadMore = movies.isNotEmpty()
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                }
        }
    }

    /**
     * "Predloženo za tebe" - lična personalizacija (Faza 4), samo za ulogovane korisnike. Backend
     * sam radi fallback na popularne ako korisnik još nema dovoljno ocena za taste profile (videti
     * MovieRouting.kt /featured) - u tom slučaju ovaj red može ličiti na "Popularni filmovi" dok
     * korisnik ne oceni par filmova, što je očekivano i samo se rešava vremenom. Bez paginacije
     * (jedna strana, ~20 filmova) - ovo je horizontalna traka, ne beskonačna vertikalna lista.
     */
    private fun loadPersonalizedMovies() {
        val userId = sessionManager.getUserId() ?: return
        personalizedPage = 1
        viewModelScope.launch {
            movieRepository.getFeaturedMovies(userId, page = personalizedPage).onSuccess { movies ->
                _uiState.value = _uiState.value.copy(personalizedMovies = movies)
            }
        }
    }

    /** "U bioskopima sada" traka - tiho ignoriše grešku, ovo je bonus sadržaj, ne kritičan poziv. */
    private fun loadNowInCinemas() {
        viewModelScope.launch {
            movieRepository.getNowInCinemas().onSuccess { movies ->
                _uiState.value = _uiState.value.copy(nowInCinemas = movies)
            }
        }
    }

    /**
     * "Nastavi da gledaš" red - čita direktno iz Room keša (observeWatchlist), isti izvor kao
     * Library ekran (Faza 3.5 offline keš), instant i radi i bez interneta. Uz to, jednom u
     * pozadini osvežimo sa mreže (write-through u Room) da red ne bude zastareo ako korisnik
     * ovu sesiju još nije otvarao Library.
     */
    private fun observeWatchlistPreview() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            userRepository.observeWatchlist(userId).collect { watchlist ->
                _uiState.value = _uiState.value.copy(watchlistPreview = watchlist.take(10))
            }
        }
        viewModelScope.launch {
            userRepository.getWatchlist(userId)
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(visionResult = null, error = null)
    }

    /**
     * Swipe-down (pull-to-refresh) na Home ekranu. NAMERNO menja samo "Predloženo za tebe" (rotira
     * TMDB discover stranu 1..MAX_PERSONALIZED_PAGE, isto kao "Osveži predloge" na Movie Night
     * ekranu) - "Popularni filmovi" i "U bioskopima sada" se ne diraju, tu nema šta novo da se
     * izvuče na zahtev (TMDB popular lista i skrapovani repertoar se ne menjaju sa svakim swipe-om),
     * pa bi ponovni fetch samo vratio isti sadržaj bez ikakve vidljive promene. Za neulogovanog
     * korisnika (nema personalizaciju) gesta i dalje kratko pokaže indikator umesto da deluje mrtvo.
     */
    fun refreshAll() {
        val userId = sessionManager.getUserId()
        if (userId == null) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isRefreshing = true)
                delay(300)
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
            return
        }

        val nextPage = if (personalizedPage >= MAX_PERSONALIZED_PAGE) 1 else personalizedPage + 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            movieRepository.getFeaturedMovies(userId, page = nextPage).onSuccess { movies ->
                personalizedPage = nextPage
                _uiState.value = _uiState.value.copy(personalizedMovies = movies)
            }
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}

