package dev.milinko.movielens.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.location.LocationHelper
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.remote.user.FriendWatched
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieDetailUiState(
    val movieDetail: MovieDetail? = null,
    val isLoading: Boolean = false,
    val isInWatchlist: Boolean = false,
    val isWatched: Boolean = false,
    // 0.0 = odgledano bez ocene (isti dogovor kao na backendu - videti UserActionsRouting.kt)
    val userRating: Double = 0.0,
    // Kratak tekstualni komentar uz ocenu (opcion) - videti HistoryItem.review.
    val userReview: String? = null,
    // Prijatelji koji su odgledali ovaj film (i njihova ocena, ako postoji). Tiho prazno ako
    // korisnik nije ulogovan ili poziv padne - nije kritična funkcionalnost, ne blokira ekran.
    val friendsWatched: List<FriendWatched> = emptyList(),
    // Bugfix (E1.2, 2. septembar 2026) - RANIJE watchlistError/historyError polja koja su se
    // prikazivala kao Toast ("Update history failed: 404" iz test feedback-a). Uklonjena namerno -
    // watchlist/watched/ocena greške se sad samo tiho loguju (Log.w), nikad ne prekidaju korisnika,
    // videti historyJob/watchlistJob napomenu ispod.
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    // Bugfix (E1.2, 2. septembar 2026, feedback testera - "greske 500/400 pri brzim uzastopnim
    // klikovima na watchlist/odgledano") - toggleWatched/rateMovie/saveReview sve mutiraju ISTI
    // backend red (user_actions/history), pa dele jedan Job. toggleWatchlist je odvojen resurs
    // (watchlist tabela) - sopstveni Job. Svaka nova akcija OTKAŽE prethodnu ako je još u letu
    // (umesto da oba zahteva odu na mrežu istovremeno i sudare se) - Retrofit suspend pozivi
    // prate coroutine cancellation, pa se i stvaran HTTP poziv prekine, ne samo lokalno stanje.
    // Rezultat: brzo uzastopno kuckanje zvezdica/dugmeta šalje SAMO poslednji zahtev, bez trke.
    private var historyJob: Job? = null
    private var watchlistJob: Job? = null

    fun loadMovieDetail(tmdbId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Poslednja poznata lokacija - samo ako korisnik već ima uključenu dozvolu (ne
            // tražimo je ovde, to radi UI preko sistemskog dijaloga). Ako je nema, showtimes
            // dolaze nesortirani po udaljenosti, ne prazni - to je samo "ukras", ne blokira ništa.
            val location = locationHelper.getLastKnownLocation()
            val userId = sessionManager.getUserId()

            // Faza C (16. avgust 2026, korisnički zahtev "da deluje momentalno pa se pozadinski
            // sa backom to uradi") - watchlist/istorija se već keširaju u Room-u (isti keš koji
            // Library ekran koristi), ovaj ekran ga ranije nije čitao - svaki put je čekao mrežni
            // round-trip da bi prikazao sopstvenu ocenu/komentar/watchlist status, uvek počinjao od
            // "nema ocene" pa "skočio" na pravu vrednost kad mreža odgovori. Sad se prvo čita
            // Room keš (instant, radi i offline), mrežni pozivi ispod i dalje idu u pozadini radi
            // osvežavanja/potvrde - `onSuccess` blokovi ispod će po potrebi prepisati ovu vrednost
            // svežijom.
            if (userId != null) {
                val cachedHistoryItem = userRepository.observeHistory(userId).first().firstOrNull { it.tmdbId == tmdbId }
                if (cachedHistoryItem != null) {
                    _uiState.value = _uiState.value.copy(
                        isWatched = true,
                        userRating = cachedHistoryItem.userRating,
                        userReview = cachedHistoryItem.review
                    )
                }
                val isCachedInWatchlist = userRepository.observeWatchlist(userId).first().any { it.tmdbId == tmdbId }
                if (isCachedInWatchlist) {
                    _uiState.value = _uiState.value.copy(isInWatchlist = true)
                }
            }

            // Detalji filma, watchlist status i istorija (odgledano/ocena) su nezavisni pozivi -
            // paralelno umesto sekvencijalno da ne čekamo tri round-trip-a zaredom.
            val detailDeferred = async { movieRepository.getMovieDetail(tmdbId, location?.latitude, location?.longitude) }
            val watchlistDeferred = userId?.let { async { userRepository.getWatchlist(it) } }
            val historyDeferred = userId?.let { async { userRepository.getHistory(it) } }
            val friendsWatchedDeferred = userId?.let { async { movieRepository.getFriendsWhoWatched(tmdbId) } }

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
                        error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri učitavanju filma")
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
                    _uiState.value = _uiState.value.copy(isWatched = true, userRating = item.userRating, userReview = item.review)
                }
            }
            friendsWatchedDeferred?.await()?.onSuccess { friends ->
                _uiState.value = _uiState.value.copy(friendsWatched = friends)
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
        val previousReview = _uiState.value.userReview
        _uiState.value = _uiState.value.copy(
            isWatched = !wasWatched,
            userRating = if (wasWatched) 0.0 else previousRating,
            userReview = if (wasWatched) null else previousReview
        )

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.removeFromHistory(userId, tmdbId)
            } else {
                userRepository.addToHistory(userId, tmdbId).map { }
            }
            // Bugfix (E1.2) - ne prikazujemo grešku korisniku za ovaj tok (videti napomenu na
            // historyJob) - najčešći uzrok je otkazan/sudaren zahtev od brzog uzastopnog klika,
            // ne stvaran problem. Tiho se loguje i lokalno stanje se vrati na poslednje poznato
            // dobro - sledeći uspešan poziv (ili sledeće otvaranje ekrana) ionako sve resinhronizuje.
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "toggleWatched nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(
                    isWatched = wasWatched,
                    userRating = previousRating,
                    userReview = previousReview
                )
            }
        }
        return true
    }

    /**
     * Postavlja/menja ocenu (1-10) i opcioni kratak tekstualni komentar - automatski označava film
     * kao odgledan ako već nije. `review = null` znači "ne diraj postojeći komentar" (npr. brza
     * izmena samo zvezdica bez otvaranja polja za komentar) - prazan string ("") eksplicitno briše
     * postojeći komentar (korisnik je namerno ispraznio polje), videti napomenu u backend
     * UserActionsRepository.markWatched. true ako je zahtev pokrenut, false ako korisnik nije ulogovan.
     */
    fun rateMovie(tmdbId: Int, rating: Int, review: String? = null): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val previousRating = _uiState.value.userRating
        val previousReview = _uiState.value.userReview
        val wasWatched = _uiState.value.isWatched
        _uiState.value = _uiState.value.copy(
            userRating = rating.toDouble(),
            userReview = if (review != null) review.ifBlank { null } else previousReview,
            isWatched = true
        )

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.updateHistoryItem(userId, tmdbId, rating.toDouble(), review)
            } else {
                userRepository.addToHistory(userId, tmdbId, rating = rating.toDouble(), review = review)
            }
            // Bugfix (E1.2) - videti napomenu na historyJob/toggleWatched: tiho se loguje umesto da
            // korisnik vidi "Update history failed: CODE" (tačan bag sa test feedback-a).
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "rateMovie nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(
                    userRating = previousRating,
                    userReview = previousReview,
                    isWatched = wasWatched
                )
            }
        }
        return true
    }

    /**
     * Čuva SAMO komentar, bez dodira na ocenu (npr. korisnik je film označio kao odgledan preko
     * `toggleWatched` bez zvezdica, i sad želi da ostavi utisak u tekstu). Šalje postojeću ocenu
     * (ako postoji) nepromenjenu - ne postoji poseban UI put za "obriši ocenu, ostani odgledano".
     * true ako je zahtev pokrenut, false ako korisnik nije ulogovan.
     */
    fun saveReview(tmdbId: Int, review: String): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val trimmedReview = review.trim().ifBlank { null }
        val previousReview = _uiState.value.userReview
        val wasWatched = _uiState.value.isWatched
        val currentRating = _uiState.value.userRating.takeIf { it > 0 }
        _uiState.value = _uiState.value.copy(
            userReview = trimmedReview,
            isWatched = true
        )

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.updateHistoryItem(userId, tmdbId, currentRating, review.trim())
            } else {
                userRepository.addToHistory(userId, tmdbId, rating = currentRating, review = review.trim())
            }
            // Bugfix (E1.2) - videti napomenu na historyJob/toggleWatched.
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "saveReview nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(
                    userReview = previousReview,
                    isWatched = wasWatched
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
        _uiState.value = _uiState.value.copy(isInWatchlist = !wasInWatchlist)

        watchlistJob?.cancel()
        watchlistJob = viewModelScope.launch {
            val result = if (wasInWatchlist) {
                userRepository.removeFromWatchlist(userId, tmdbId).map { }
            } else {
                userRepository.addToWatchlist(userId, tmdbId).map { }
            }
            // Bugfix (E1.2) - videti napomenu na historyJob/toggleWatched, isti princip za watchlist.
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "toggleWatchlist nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(isInWatchlist = wasInWatchlist)
            }
        }
        return true
    }
}

