package dev.milinko.movielens.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieDetailUiState(
    val movieDetail: MovieDetail? = null,
    val isLoading: Boolean = false,
    val isInWatchlist: Boolean = false,
    val isWatched: Boolean = false,
    // 0.0 = odgledano bez ocene (isti dogovor kao na backendu - videti UserActionsRouting.kt)
    val userRating: Double = 0.0,
    val watchlistError: String? = null,
    val historyError: String? = null,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    fun loadMovieDetail(tmdbId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Detalji filma, watchlist status i istorija (odgledano/ocena) su nezavisni pozivi -
            // paralelno umesto sekvencijalno da ne čekamo tri round-trip-a zaredom.
            val detailDeferred = async { movieRepository.getMovieDetail(tmdbId) }
            val userId = sessionManager.getUserId()
            val watchlistDeferred = userId?.let { async { userRepository.getWatchlist(it) } }
            val historyDeferred = userId?.let { async { userRepository.getHistory(it) } }

            detailDeferred.await()
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movieDetail = detail
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Greška pri učitavanju filma"
                    )
                }

            // Ako je korisnik ulogovan, proveri da li je film već u watchlisti/istoriji. Nema
            // posebnog "da li je ovaj film..." endpoint-a, pa gledamo pune liste - jeftino je
            // (po jedan poziv po otvaranju detalja) i tačno odražava stanje umesto da uvek kreće
            // od "nije u watchlisti"/"nije odgledano".
            watchlistDeferred?.await()?.onSuccess { items ->
                _uiState.value = _uiState.value.copy(
                    isInWatchlist = items.any { it.tmdbId == tmdbId }
                )
            }
            historyDeferred?.await()?.onSuccess { items ->
                val item = items.firstOrNull { it.tmdbId == tmdbId }
                if (item != null) {
                    _uiState.value = _uiState.value.copy(isWatched = true, userRating = item.userRating)
                }
            }
        }
    }

    /**
     * Uključuje/isključuje "odgledano". Drugi klik (kad je već odgledano) je "undo" - za slučaj
     * lapsus klika - i briše i ocenu ako je bila postavljena (ista tabela/red na backendu). true
     * ako je zahtev pokrenut, false ako korisnik nije ulogovan.
     */
    fun toggleWatched(tmdbId: Int): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val wasWatched = _uiState.value.isWatched
        val previousRating = _uiState.value.userRating
        _uiState.value = _uiState.value.copy(
            isWatched = !wasWatched,
            userRating = if (wasWatched) 0.0 else previousRating,
            historyError = null
        )

        viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.removeFromHistory(userId, tmdbId)
            } else {
                userRepository.addToHistory(userId, tmdbId).map { }
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isWatched = wasWatched,
                    userRating = previousRating,
                    historyError = e.message ?: "Greška pri ažuriranju statusa odgledano"
                )
            }
        }
        return true
    }

    /**
     * Postavlja/menja ocenu (1-10) - automatski označava film kao odgledan ako već nije. true ako
     * je zahtev pokrenut, false ako korisnik nije ulogovan.
     */
    fun rateMovie(tmdbId: Int, rating: Int): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val previousRating = _uiState.value.userRating
        val wasWatched = _uiState.value.isWatched
        _uiState.value = _uiState.value.copy(
            userRating = rating.toDouble(),
            isWatched = true,
            historyError = null
        )

        viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.updateHistoryItem(userId, tmdbId, rating.toDouble())
            } else {
                userRepository.addToHistory(userId, tmdbId, rating = rating.toDouble())
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    userRating = previousRating,
                    isWatched = wasWatched,
                    historyError = e.message ?: "Greška pri ažuriranju ocene"
                )
            }
        }
        return true
    }

    /**
     * true ako korisnik nije ulogovan (poziv nije ni pokušan) - ekran treba da traži prijavu.
     * Ažurira UI odmah (optimistički), pre nego što mrežni poziv uopšte završi - dugme reaguje
     * trenutno na klik umesto da čeka round-trip. Ako poziv ne uspe, stanje se vraća nazad.
     */
    fun toggleWatchlist(tmdbId: Int): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val wasInWatchlist = _uiState.value.isInWatchlist
        _uiState.value = _uiState.value.copy(isInWatchlist = !wasInWatchlist, watchlistError = null)

        viewModelScope.launch {
            val result = if (wasInWatchlist) {
                userRepository.removeFromWatchlist(userId, tmdbId).map { }
            } else {
                userRepository.addToWatchlist(userId, tmdbId).map { }
            }
            result.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isInWatchlist = wasInWatchlist,
                    watchlistError = e.message ?: "Greška pri ažuriranju watchlist-e"
                )
            }
        }
        return true
    }
}

