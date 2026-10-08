package dev.milinko.movielens.ui.detail

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.location.LocationHelper
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieComment
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
    // Prijatelji koji su odgledali ovaj film (i njihova ocena, ako postoji). Tiho prazno ako
    // korisnik nije ulogovan ili poziv padne - nije kritična funkcionalnost, ne blokira ekran.
    val friendsWatched: List<FriendWatched> = emptyList(),
    // E2.2 dorada (3. septembar 2026) - javni komentari, ZAMENJUJU stari userReview koncept
    // (jedan privatan tekst uz ocenu). Potpuno odvojeno od ocene/watched statusa ispod.
    // `comments` su SVI do sada mrežno učitani komentari; `commentsVisibleCount` je koliko se od
    // njih trenutno prikazuje - "Prikaži više" prvo samo otkriva već učitane (bez mreže), tek kad
    // ih ponestane a `commentsHasMoreOnServer` je true ide se po sledeću stranicu. "Prikaži manje"
    // samo vraća vidljivi broj na COMMENTS_PAGE_SIZE, bez brisanja već učitanih iz memorije.
    val comments: List<MovieComment> = emptyList(),
    val commentsVisibleCount: Int = 0,
    // Tekst komentara koji server nije prihvatio - ekran ga vraća u polje i javlja poruku
    // (optimistična objava, videti postComment).
    val failedCommentText: String? = null,
    val commentsHasMoreOnServer: Boolean = false,
    val isLoadingComments: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
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
    // klikovima na watchlist/odgledano") - toggleWatched/rateMovie sve mutiraju ISTI
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
                        userRating = cachedHistoryItem.userRating
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
                        error = dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_load_movie)
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
            friendsWatchedDeferred?.await()?.onSuccess { friends ->
                _uiState.value = _uiState.value.copy(friendsWatched = friends)
            }

            loadComments(tmdbId)
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
            userRating = if (wasWatched) 0.0 else previousRating
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
                    userRating = previousRating
                )
            }
        }
        return true
    }

    /**
     * Postavlja/menja ocenu (1-10) - automatski označava film kao odgledan ako već nije. Kratak
     * tekstualni "komentar uz ocenu" je uklonjen (E2.2 dorada, 3. septembar 2026) - zamenjen
     * potpuno odvojenim javnim komentarima ispod, videti loadComments/postComment. true ako je
     * zahtev pokrenut, false ako korisnik nije ulogovan.
     */
    fun rateMovie(tmdbId: Int, rating: Int): Boolean {
        val userId = sessionManager.getUserId() ?: return false

        val previousRating = _uiState.value.userRating
        val wasWatched = _uiState.value.isWatched
        _uiState.value = _uiState.value.copy(
            userRating = rating.toDouble(),
            isWatched = true
        )

        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            val result = if (wasWatched) {
                userRepository.updateHistoryItem(userId, tmdbId, rating.toDouble())
            } else {
                userRepository.addToHistory(userId, tmdbId, rating = rating.toDouble())
            }
            // Bugfix (E1.2) - videti napomenu na historyJob/toggleWatched: tiho se loguje umesto da
            // korisnik vidi "Update history failed: CODE" (tačan bag sa test feedback-a).
            result.onFailure { e ->
                Log.w("MovieDetailViewModel", "rateMovie nije uspeo (tiho, ne prikazuje se korisniku): ${e.message}")
                _uiState.value = _uiState.value.copy(
                    userRating = previousRating,
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

    // E2.2 dorada (3. septembar 2026) - javni komentari na film. Videti obrazloženje na
    // MovieDetailUiState.comments/commentsVisibleCount iznad za "otkrij već učitano pre nego što
    // se ide po sledeću mrežnu stranicu" logiku.

    private fun loadComments(tmdbId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingComments = true)
            movieRepository.getComments(tmdbId, offset = 0, limit = COMMENTS_PAGE_SIZE)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        comments = response.comments,
                        commentsVisibleCount = response.comments.size,
                        commentsHasMoreOnServer = response.hasMore,
                        isLoadingComments = false
                    )
                }
                .onFailure { e ->
                    // Tiho, isti princip kao friendsWatched - komentari nisu kritični, ne
                    // blokiraju ostatak ekrana koji se već učitao.
                    Log.w("MovieDetailViewModel", "loadComments nije uspeo (tiho): ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoadingComments = false)
                }
        }
    }

    /**
     * "Prikaži više" - prvo samo otkriva već mrežno učitane komentare (bez poziva), tek kad
     * ponestane a server ima još ide se po sledeću stranicu.
     */
    fun showMoreComments(tmdbId: Int) {
        val state = _uiState.value
        if (state.commentsVisibleCount < state.comments.size) {
            _uiState.value = state.copy(
                commentsVisibleCount = (state.commentsVisibleCount + COMMENTS_PAGE_SIZE).coerceAtMost(state.comments.size)
            )
            return
        }
        if (!state.commentsHasMoreOnServer || state.isLoadingMoreComments) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingMoreComments = true)
            movieRepository.getComments(tmdbId, offset = state.comments.size, limit = COMMENTS_PAGE_SIZE)
                .onSuccess { response ->
                    val merged = _uiState.value.comments + response.comments
                    _uiState.value = _uiState.value.copy(
                        comments = merged,
                        commentsVisibleCount = (_uiState.value.commentsVisibleCount + COMMENTS_PAGE_SIZE).coerceAtMost(merged.size),
                        commentsHasMoreOnServer = response.hasMore,
                        isLoadingMoreComments = false
                    )
                }
                .onFailure { e ->
                    Log.w("MovieDetailViewModel", "showMoreComments nije uspeo (tiho): ${e.message}")
                    _uiState.value = _uiState.value.copy(isLoadingMoreComments = false)
                }
        }
    }

    /** "Prikaži manje" - vraća vidljivi broj na prvu stranicu, bez brisanja iz memorije/mreže. */
    fun showLessComments() {
        val state = _uiState.value
        _uiState.value = state.copy(commentsVisibleCount = COMMENTS_PAGE_SIZE.coerceAtMost(state.comments.size))
    }

    /**
     * Objavljuje novi komentar - novi komentar se odmah dodaje na vrh vidljive liste (optimistički,
     * pre nego što mreža potvrdi) da se ekran oseti trenutno responzivan. true ako je zahtev
     * pokrenut, false ako korisnik nije ulogovan ili je tekst prazan.
     */
    fun postComment(tmdbId: Int, body: String): Boolean {
        val userId = sessionManager.getUserId() ?: return false
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false

        // UX dorada (oktobar 2026): komentar se prikazuje ODMAH (privremeni, negativan id dok server
        // ne dodeli pravi). Ranije se pojavljivao tek posle mreže, a pri grešci je tiho nestajao
        // zajedno sa tekstom.
        val tempId = nextTempCommentId--
        val optimistic = MovieComment(
            id = tempId,
            userId = userId,
            username = sessionManager.getUsername() ?: "",
            avatarUrl = sessionManager.getAvatarUrl(),
            body = trimmed,
            createdAt = System.currentTimeMillis(),
            isOwn = true
        )
        _uiState.value = _uiState.value.let { state ->
            state.copy(
                comments = listOf(optimistic) + state.comments,
                commentsVisibleCount = state.commentsVisibleCount + 1,
                failedCommentText = null
            )
        }

        viewModelScope.launch {
            movieRepository.addComment(tmdbId, trimmed)
                .onSuccess { comment ->
                    _uiState.value = _uiState.value.let { state ->
                        state.copy(comments = state.comments.map { if (it.id == tempId) comment else it })
                    }
                }
                .onFailure { e ->
                    Log.w("MovieDetailViewModel", "postComment nije uspeo, vraćam tekst u polje: ${e.message}")
                    _uiState.value = _uiState.value.let { state ->
                        state.copy(
                            comments = state.comments.filterNot { it.id == tempId },
                            commentsVisibleCount = (state.commentsVisibleCount - 1).coerceAtLeast(0),
                            failedCommentText = trimmed
                        )
                    }
                }
        }
        return true
    }

    /** Ekran je vratio neuspeli tekst u polje i prikazao poruku. */
    fun consumeFailedComment() {
        _uiState.value = _uiState.value.copy(failedCommentText = null)
    }

    // Privremeni (lokalni) id-jevi optimističnih komentara - negativni da se ne sudare sa pravim.
    private var nextTempCommentId = -1

    /** Briše sopstveni komentar (optimistički, uz rollback ako mreža odbije). */
    fun deleteComment(commentId: Int) {
        // Komentar još nije stigao do servera (privremeni id) - nema šta da se briše tamo.
        if (commentId < 0) return
        val state = _uiState.value
        val index = state.comments.indexOfFirst { it.id == commentId }
        if (index == -1) return
        val removed = state.comments[index]
        _uiState.value = state.copy(
            comments = state.comments.filterNot { it.id == commentId },
            commentsVisibleCount = (state.commentsVisibleCount - 1).coerceAtLeast(0)
        )

        viewModelScope.launch {
            movieRepository.deleteComment(commentId).onFailure { e ->
                Log.w("MovieDetailViewModel", "deleteComment nije uspeo, vraćam komentar nazad: ${e.message}")
                val current = _uiState.value
                _uiState.value = current.copy(
                    comments = current.comments.toMutableList().apply { add(index.coerceAtMost(size), removed) },
                    commentsVisibleCount = current.commentsVisibleCount + 1
                )
            }
        }
    }

    /**
     * Lajk/skidanje lajka (toggle) - optimistički menja srce/brojač odmah, uz rollback ako mreža
     * odbije (npr. pokušaj lajkovanja sopstvenog komentara - backend to odbija, videti
     * CommentsRepository.toggleLike). Ne deli Job sa ostalima - lajkovi na različitim komentarima
     * su nezavisni resursi, za razliku od historyJob/watchlistJob slučaja.
     */
    fun toggleCommentLike(commentId: Int) {
        if (commentId < 0) return // privremeni komentar, još nije na serveru
        val state = _uiState.value
        val comment = state.comments.firstOrNull { it.id == commentId } ?: return
        val optimisticLiked = !comment.likedByMe
        val optimisticCount = (comment.likeCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0)
        _uiState.value = state.copy(
            comments = state.comments.map {
                if (it.id == commentId) it.copy(likedByMe = optimisticLiked, likeCount = optimisticCount) else it
            }
        )

        viewModelScope.launch {
            movieRepository.toggleCommentLike(commentId)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        comments = _uiState.value.comments.map {
                            if (it.id == commentId) it.copy(likedByMe = result.likedByMe, likeCount = result.likeCount) else it
                        }
                    )
                }
                .onFailure { e ->
                    Log.w("MovieDetailViewModel", "toggleCommentLike nije uspeo, vraćam nazad: ${e.message}")
                    _uiState.value = _uiState.value.copy(
                        comments = _uiState.value.comments.map {
                            if (it.id == commentId) it.copy(likedByMe = comment.likedByMe, likeCount = comment.likeCount) else it
                        }
                    )
                }
        }
    }

    companion object {
        // Komentari se učitavaju i otkrivaju po 3; isti prag koristi i "Prikaži manje" na ekranu.
        internal const val COMMENTS_PAGE_SIZE = 3
    }
}

