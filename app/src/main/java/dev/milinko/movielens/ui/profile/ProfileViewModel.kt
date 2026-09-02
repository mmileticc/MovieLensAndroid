package dev.milinko.movielens.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.pref.ThemePreferences
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.GenreAffinity
import dev.milinko.movielens.data.remote.user.PendingRequest
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSearchResult
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val friends: List<FriendInfo> = emptyList(),
    val friendActivity: List<FriendActivity> = emptyList(),
    val pendingRequests: List<PendingRequest> = emptyList(),
    // Korisnički zahtev (2. septembar 2026, "da mi budu vidljivi izlistani poslati zahtevi kao
    // sto su mi izlistani prijatelji, a ne samo da kad ponovo pretrazim osobu vidim da sam mu
    // vec poslao zahtev") - poslati (odlazni) zahtevi, prikazani u posebnoj sekciji.
    val sentRequests: List<PendingRequest> = emptyList(),
    // Taste profile (Faza 4) - top žanrovi po prosečnoj oceni, izvedeno iz korisnikovih ocena.
    val tasteProfile: List<GenreAffinity> = emptyList(),
    // Search-as-you-type za dodavanje prijatelja (isti obrazac kao SearchViewModel za filmove).
    val friendSearchQuery: String = "",
    val friendSearchResults: List<UserSearchResult> = emptyList(),
    val isSearchingFriends: Boolean = false,
    val friendActionError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // Uređivanje profila (username/bio) - Faza 7 redizajna, MOVIELENS_DESIGN_PLAN.md.
    val isSavingProfile: Boolean = false,
    val editProfileError: String? = null,
    // Bugfix (E1.4, 2. septembar 2026, feedback testera - "lista prijatelja se ne ažurira odmah
    // posle prihvatanja, mora da se promeni ekran ili zatvori app") - odvojeno od `isLoading`
    // (koje pokriva prvi ikad ulazak na ekran) - swipe-down gest na Prijatelji tabu.
    val isRefreshingFriends: Boolean = false,
    // Bugfix (2. septembar 2026, feedback - "posalji/otkazi rapidno, dodaj posle radi sporo i
    // nikako da osvezi") - id-jevi ciljnih korisnika za koje je zahtev trenutno u letu. Dok je
    // neki id ovde, dugme za njega je onemogućeno (videti UserSearchResultRow) - sprečava da
    // korisnik pokrene SUPROTNU akciju (npr. otkaži) dok je prethodna (pošalji) još na putu ka
    // sporom Render backendu, što je ranije otkazivalo prethodni mrežni poziv usred leta i
    // rasinhronizovalo klijent/server (videti FriendProfileViewModel za detaljno objašnjenje).
    val friendActionInFlightIds: Set<String> = emptySet()
)

/**
 * Osnovni identitet ulogovanog korisnika, čuvan lokalno u SessionManager-u odmah nakon
 * Firebase/backend auth exchange-a (vidi AuthViewModel.finishGoogleSignIn). Ne zavisi od
 * GET /api/users/{id} (koji jos ne postoji na backendu) - prikazuje se odmah, bez čekanja mreže.
 */
data class SessionUserInfo(
    val username: String?,
    val email: String?,
    val avatarUrl: String?
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val urlStorage: UrlStorage,
    private val sessionManager: SessionManager,
    private val themePreferences: ThemePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Faza 2 (light tema) - lokalno podešavanje, nezavisno od backend-a (koji nikad nije imao
    // /settings rutu, videti ThemePreferences.kt), pa Settings sekcija sad uvek može da ga prikaže.
    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkMode

    private val _sessionUser = MutableStateFlow(readSessionUser())
    val sessionUser: StateFlow<SessionUserInfo?> = _sessionUser.asStateFlow()

    // Expose base URL from UrlStorage so UI can edit backend address during testing
    private val _baseUrl = MutableStateFlow(urlStorage.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _savedRoutes = MutableStateFlow(urlStorage.getSavedRoutes())
    val savedRoutes: StateFlow<List<String>> = _savedRoutes.asStateFlow()

    private fun readSessionUser(): SessionUserInfo? {
        val userId = sessionManager.getUserId() ?: return null
        return SessionUserInfo(
            username = sessionManager.getUsername(),
            email = sessionManager.getEmail(),
            avatarUrl = sessionManager.getAvatarUrl()
        )
    }

    /** Poziva se pri ponovnom ulasku na Profile ekran (npr. nakon prijave/odjave). */
    fun refreshSessionUser() {
        _sessionUser.value = readSessionUser()
    }

    // One-shot UI events (toasts, messages)
    private val _events = MutableSharedFlow<ProfileUiEvent>()
    val events = _events.asSharedFlow()

    sealed class ProfileUiEvent {
        data class ShowMessage(val message: String) : ProfileUiEvent()
    }

    // Debounce posao za pretragu prijatelja dok korisnik kuca - isti obrazac kao SearchViewModel.
    private var friendSearchJob: Job? = null

    // Bugfix (E1.3, 2. septembar 2026, feedback testera - "greske 40x/50x pri prijateljstvima",
    // isti obrazac kao E1.2/MovieDetailViewModel) - guard protiv duplih tapova, keyed po
    // requesterId (accept/reject su drugi smer akcije od send/cancel, videti
    // friendActionInFlightIds u ProfileUiState za send/cancel guard).
    private val pendingActionJobs = mutableMapOf<String, Job>()

    private companion object {
        const val FRIEND_SEARCH_DEBOUNCE_MS = 300L
        const val MIN_FRIEND_QUERY_LENGTH = 2
    }

    fun setFriendSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(friendSearchQuery = query)

        friendSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_FRIEND_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(friendSearchResults = emptyList(), isSearchingFriends = false)
            return
        }

        friendSearchJob = viewModelScope.launch {
            delay(FRIEND_SEARCH_DEBOUNCE_MS)
            _uiState.value = _uiState.value.copy(isSearchingFriends = true)
            userRepository.searchUsers(trimmed)
                .onSuccess { results ->
                    _uiState.value = _uiState.value.copy(isSearchingFriends = false, friendSearchResults = results)
                }
                .onFailure {
                    // Tiho - CancellationException (otkazana pretraga zbog novog kucanja) se ne
                    // prikazuje kao greška, isti razlog kao kod SearchViewModel/safeApiCall.
                    _uiState.value = _uiState.value.copy(isSearchingFriends = false)
                }
        }
    }

    fun clearFriendSearch() {
        friendSearchJob?.cancel()
        _uiState.value = _uiState.value.copy(friendSearchQuery = "", friendSearchResults = emptyList(), isSearchingFriends = false)
    }

    /**
     * Bugfix (2. septembar 2026, feedback - "pošalje se zahtev sa profila prijatelja, vrati se
     * nazad na Profile, dugme u rezultatima pretrage i dalje piše staro stanje") - ponovo pokreće
     * poslednju pretragu (bez debounce-a, bez spinnera - tiho u pozadini) da bi se status
     * (Dodaj/Otkaži zahtev) osvežio ako je promenjen na nekom drugom ekranu dok je ova ViewModel
     * instanca ostala živa. No-op ako trenutno nema aktivnog upita.
     */
    fun refreshFriendSearchResults() {
        val userId = sessionManager.getUserId() ?: return
        val trimmed = _uiState.value.friendSearchQuery.trim()
        if (trimmed.length < MIN_FRIEND_QUERY_LENGTH) return

        // Dorada (2. septembar 2026, feedback - "spor prelaz nazad na Profile, treba par sekundi
        // dok se osveži") - `searchUsers()` ide na mrežu (Render cold-start ume da traje par
        // sekundi), pa je prikaz dugmeta čekao ceo taj round-trip. Za rezultate čiji profil VEĆ
        // imamo u lokalnom Room kešu (znači korisnik ga je upravo otvorio i tamo poslao/otkazao
        // zahtev - videti FriendProfileViewModel.cacheFriendProfile), status se preuzima iz keša
        // ODMAH, instant, bez čekanja na mrežu. Mrežni poziv ispod i dalje ide u pozadini kao
        // autoritativna potvrda (npr. hvata promene sa DRUGOG uređaja).
        viewModelScope.launch {
            val current = _uiState.value.friendSearchResults
            val merged = current.map { result ->
                userRepository.observeCachedFriendProfile(userId, result.id).first()
                    ?.friendshipStatus?.let { status -> result.copy(status = status) } ?: result
            }
            if (merged != current) {
                _uiState.value = _uiState.value.copy(friendSearchResults = merged)
            }
        }

        friendSearchJob?.cancel()
        friendSearchJob = viewModelScope.launch {
            userRepository.searchUsers(trimmed).onSuccess { results ->
                _uiState.value = _uiState.value.copy(friendSearchResults = results)
            }
        }
    }

    /**
     * Otkazuje već poslat zahtev (PENDING_SENT -> NONE u rezultatima pretrage). Backend nema
     * poseban "cancel" - isti DELETE endpoint koji se koristi za unfriend/reject briše red
     * bez obzira na smer/status, pa radi i ovde.
     */
    fun cancelFriendRequest(targetUserId: String) {
        // Bugfix (2. septembar 2026, feedback - "posalji/otkazi rapidno, dodaj posle radi sporo i
        // nikako da osvezi") - RANIJE se prethodni let posao za isti targetUserId OTKAZIVAO da bi
        // se odmah pokrenuo suprotni (send<->cancel). Problem: mrežni poziv može već biti poslat
        // ka sporom Render backendu pre nego što lokalni cancel() stigne da ga prekine - server ga
        // ipak obradi, dok klijent (zbog cancel-a) nikad ne dobije povratni poziv da sinhronizuje
        // UI. Fix: dok je akcija za taj id u letu, novi tap se IGNORIŠE (dugme je i vizuelno
        // onemogućeno - videti friendActionInFlightIds/UserSearchResultRow) umesto da se
        // prethodni poziv prekida usred leta.
        if (targetUserId in _uiState.value.friendActionInFlightIds) return
        val userId = sessionManager.getUserId() ?: return
        val previousResults = _uiState.value.friendSearchResults
        // Korisnički zahtev (2. septembar 2026) - poslata lista se ažurira optimistički zajedno
        // sa rezultatima pretrage, ista logika kao friendSearchResults ispod (bez mreže, odmah).
        val previousSent = _uiState.value.sentRequests
        _uiState.value = _uiState.value.copy(
            friendSearchResults = previousResults.map { if (it.id == targetUserId) it.copy(status = "NONE") else it },
            sentRequests = previousSent.filterNot { it.id == targetUserId },
            friendActionInFlightIds = _uiState.value.friendActionInFlightIds + targetUserId
        )
        viewModelScope.launch {
            // Bugfix (2. septembar 2026, feedback - "otkažem kod prijatelja, uđem na detalje - nije
            // se promenilo, vratim se nazad - i tu bude opet pogrešno") - piše i u isti Room keš koji
            // FriendProfileScreen čita, da se izmena odavde vidi i tamo, i da povratak ovde (preko
            // refreshFriendSearchResults cache-merge-a) ne prepiše ispravno stanje starim kešom.
            userRepository.updateCachedFriendshipStatus(userId, targetUserId, "NONE")
            userRepository.removeFriend(userId, targetUserId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friendSearchResults = previousResults,
                    sentRequests = previousSent,
                    friendActionError = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri otkazivanju zahteva")
                )
                userRepository.updateCachedFriendshipStatus(userId, targetUserId, "PENDING_SENT")
            }
            _uiState.value = _uiState.value.copy(friendActionInFlightIds = _uiState.value.friendActionInFlightIds - targetUserId)
        }
    }

    /** Optimistički - status rezultata pretrage se odmah menja u PENDING_SENT, vraća se ako padne. */
    fun sendFriendRequest(targetUserId: String) {
        // Bugfix (2. septembar 2026) - videti napomenu u cancelFriendRequest().
        if (targetUserId in _uiState.value.friendActionInFlightIds) return
        val userId = sessionManager.getUserId() ?: return
        val previousResults = _uiState.value.friendSearchResults
        // Korisnički zahtev (2. septembar 2026, "da mi budu vidljivi izlistani poslati zahtevi
        // kao sto su mi izlistani prijatelji") - dodaje se odmah (optimistički, bez mreže) u
        // sentRequests, građeno iz istog search rezultata (ima id/username/avatarUrl).
        val previousSent = _uiState.value.sentRequests
        val targetResult = previousResults.find { it.id == targetUserId }
        val newSentEntry = targetResult?.let { PendingRequest(id = it.id, username = it.username, avatarUrl = it.avatarUrl) }
        _uiState.value = _uiState.value.copy(
            friendSearchResults = previousResults.map { if (it.id == targetUserId) it.copy(status = "PENDING_SENT") else it },
            sentRequests = if (newSentEntry != null) previousSent + newSentEntry else previousSent,
            friendActionInFlightIds = _uiState.value.friendActionInFlightIds + targetUserId
        )
        viewModelScope.launch {
            // Bugfix (2. septembar 2026) - videti napomenu u cancelFriendRequest().
            userRepository.updateCachedFriendshipStatus(userId, targetUserId, "PENDING_SENT")
            userRepository.addFriend(userId, targetUserId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friendSearchResults = previousResults,
                    sentRequests = previousSent,
                    friendActionError = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri slanju zahteva za prijateljstvo")
                )
                userRepository.updateCachedFriendshipStatus(userId, targetUserId, "NONE")
            }
            _uiState.value = _uiState.value.copy(friendActionInFlightIds = _uiState.value.friendActionInFlightIds - targetUserId)
        }
    }

    /** Optimistički uklanja zahtev iz liste na klik, prihvatanje osvežava listu prijatelja. */
    fun acceptFriendRequest(requesterId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousPending = _uiState.value.pendingRequests
        _uiState.value = _uiState.value.copy(pendingRequests = previousPending.filterNot { it.id == requesterId })
        pendingActionJobs[requesterId]?.cancel()
        pendingActionJobs[requesterId] = viewModelScope.launch {
            userRepository.acceptFriendRequest(userId, requesterId)
                .onSuccess {
                    // Dorada (2. septembar 2026, ista klasa buga kao sendFriendRequest/
                    // cancelFriendRequest ranije u ovom fajlu) - RANIJE se ovde pisalo samo u
                    // memoriju (pendingRequests/friends), NIKAD u friendprofile Room keš koji
                    // FriendProfileScreen čita - pa bi taj ekran (ako se otvori odmah posle
                    // prihvatanja) kratko prikazao stari "Odgovori ispod"/PENDING_RECEIVED status
                    // dok mreža ne stigne da ispravi. Zatvara petlju u istom smeru kao dorada #5.
                    userRepository.updateCachedFriendshipStatus(userId, requesterId, "ACCEPTED")
                    userRepository.getFriends(userId).onSuccess { friends ->
                        _uiState.value = _uiState.value.copy(friends = friends)
                    }
                }
                .onFailure { e ->
                    // Bugfix (E1.3) - RANIJE se stavka vraćala nazad u pendingRequests
                    // (`previousPending`) na svaki neuspeh, pa je isti zahtev mogao ponovo da se
                    // klikne i opet padne - baš "greške 40x/50x u krug" iz test feedback-a. Sad se
                    // NE vraća: ako je backend odbio (npr. red uopšte nije bio pravi "meni upućen"
                    // zahtev za prihvatanje - sumnja na sinhronizacioni bag, istražuje se), stavka
                    // ostaje uklonjena iz liste umesto da se korisniku nudi da opet pukne na istu
                    // grešku. Greška se samo tiho loguje, ne prikazuje se korisniku.
                    Log.w("ProfileViewModel", "acceptFriendRequest nije uspeo za requesterId=$requesterId (tiho, ne prikazuje se korisniku): ${e.message}")
                }
        }
    }

    fun rejectFriendRequest(requesterId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousPending = _uiState.value.pendingRequests
        _uiState.value = _uiState.value.copy(pendingRequests = previousPending.filterNot { it.id == requesterId })
        pendingActionJobs[requesterId]?.cancel()
        pendingActionJobs[requesterId] = viewModelScope.launch {
            // Bugfix (E1.3) - isti princip kao acceptFriendRequest, videti napomenu tamo.
            userRepository.rejectFriendRequest(userId, requesterId)
                .onSuccess {
                    // Dorada (2. septembar 2026) - videti napomenu u acceptFriendRequest().
                    userRepository.updateCachedFriendshipStatus(userId, requesterId, "NONE")
                }
                .onFailure { e ->
                    Log.w("ProfileViewModel", "rejectFriendRequest nije uspeo za requesterId=$requesterId (tiho, ne prikazuje se korisniku): ${e.message}")
                }
        }
    }

    fun removeFriend(friendId: String) {
        val userId = sessionManager.getUserId() ?: return
        val previousFriends = _uiState.value.friends
        _uiState.value = _uiState.value.copy(friends = previousFriends.filterNot { it.friendId == friendId })
        viewModelScope.launch {
            userRepository.removeFriend(userId, friendId).onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    friends = previousFriends,
                    friendActionError = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri uklanjanju prijatelja")
                )
            }
        }
    }

    fun consumeFriendActionError() {
        _uiState.value = _uiState.value.copy(friendActionError = null)
    }

    /**
     * Čuva novi username/bio/avatar (Faza 7 redizajna + dorada) - GET/PUT /api/users/{userId}/profile.
     * Android Retrofit ugovor šalje ceo `UserProfile` objekat (postojeći `updateUserProfile` potpis),
     * pa ovde uzimamo trenutno učitan profil kao osnovu i menjamo username/bio (i avatar, samo ako
     * je korisnik stvarno izabrao novu sliku - `newAvatarDataUri == null` znači "ostavi trenutnu
     * sliku netaknutu", videti `UserDao.updateProfile` na backendu). Backend ionako ignoriše ostala
     * polja (stats/favoriteGenres se uvek izvode server-side, ne pišu se ovom rutom). Greška (npr.
     * "Korisničko ime je već zauzeto") se vraća sa backend-a kroz `UserRepositoryImpl.parseErrorMessage`
     * i prikazuje se direktno korisniku.
     */
    fun updateProfile(username: String, bio: String?, newAvatarDataUri: String? = null) {
        val userId = sessionManager.getUserId() ?: return
        val current = _uiState.value.userProfile
        val base = current ?: dev.milinko.movielens.data.remote.user.UserProfile(
            userId = userId,
            username = username,
            email = sessionManager.getEmail() ?: ""
        )
        val body = base.copy(
            username = username,
            bio = bio,
            avatar = newAvatarDataUri ?: base.avatar
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSavingProfile = true, editProfileError = null)
            userRepository.updateUserProfile(userId, body)
                .onSuccess { updated ->
                    _uiState.value = _uiState.value.copy(isSavingProfile = false, userProfile = updated)
                    // Bugfix (16. avgust 2026, korisnička žalba: pogrešna/stara slika na svom
                    // profilu dok se sveži podatak ne učita) - SessionManager keš (avatarUrl,
                    // username) se ranije upisivao SAMO pri loginu i nikad posle nije osvežavan,
                    // pa je EditableProfileHeader-ov fallback (dok fullProfile još nije stigao sa
                    // mreže) prikazivao trajno zastarelu sliku. Sad se keš odmah osveži posle
                    // uspešnog čuvanja, pa je fallback tačan i pri sledećem otvaranju ekrana.
                    sessionManager.updateCachedIdentity(updated.username, updated.avatar)
                    refreshSessionUser()
                    _events.emit(ProfileUiEvent.ShowMessage("Profil je sačuvan."))
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isSavingProfile = false,
                        editProfileError = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri čuvanju profila")
                    )
                }
        }
    }

    fun consumeEditProfileError() {
        _uiState.value = _uiState.value.copy(editProfileError = null)
    }

    private fun refreshRoutesState() {
        _baseUrl.value = urlStorage.getBaseUrl()
        _savedRoutes.value = urlStorage.getSavedRoutes()
    }

    fun updateBaseUrl(newUrl: String) {
        viewModelScope.launch {
            try {
                urlStorage.setBaseUrl(newUrl)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Backend URL sačuvan."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Dorada (2. septembar 2026) - `e.message` se ranije ulepljivao direktno u
                // poruku, bez prevoda - `friendlyMessage()` sad garantuje da nikad ne procuri
                // sirov tehnički tekst, isti obrazac kao svuda drugde u appu.
                _events.emit(ProfileUiEvent.ShowMessage(dev.milinko.movielens.util.friendlyMessage(e, "Greška pri čuvanju URL-a.")))
            }
        }
    }

    fun addSavedRoute(url: String) {
        viewModelScope.launch {
            try {
                urlStorage.addRoute(url)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Ruta je dodata u listu."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage(dev.milinko.movielens.util.friendlyMessage(e, "Greška pri dodavanju rute.")))
            }
        }
    }

    fun removeSavedRoute(url: String) {
        viewModelScope.launch {
            try {
                urlStorage.removeRoute(url)
                refreshRoutesState()
                _events.emit(ProfileUiEvent.ShowMessage("Ruta je obrisana."))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage(dev.milinko.movielens.util.friendlyMessage(e, "Greška pri brisanju rute.")))
            }
        }
    }

    /** Učitava profil trenutno ulogovanog korisnika (iz SessionManager-a). */
    // Bugfix (E1.4, 2. septembar 2026) - `forceRefresh` dodat da bi pull-to-refresh mogao da
    // zaobiđe guard ispod. Taj guard je razlog "mora da se promeni ekran" iz test feedback-a: dok
    // je ista ViewModel instanca živa (npr. korisnik ostane na istom Profile ekranu), `userProfile`
    // ostaje isti pa se sledeći poziv tiho ignoriše - i to je NAMERNO za normalnu navigaciju (da se
    // ne bombarduje mreža svaki put kad se tab ponovo prikaže), ali onemogućava eksplicitan
    // korisnički zahtev za osvežavanje.
    fun loadProfile(forceRefresh: Boolean = false) {
        refreshSessionUser()
        // Bugfix (2. septembar 2026, feedback - "posalje se zahtev sa profila prijatelja, vrati
        // se nazad na Profile, a dugme u rezultatima pretrage i dalje piše staro stanje") -
        // friendSearchResults je nezavisan od guard-a ispod (koji sprečava ponovni fetch CELOG
        // profila), pa se poziva odvojeno na svaki ulazak na ekran - jeftino je (no-op ako nema
        // aktivnog upita) i hvata slučaj kad je status promenjen na nekom drugom ekranu
        // (FriendProfileScreen) dok je ova ViewModel instanca ostala živa u pozadini.
        refreshFriendSearchResults()
        val userId = sessionManager.getUserId() ?: return

        // avoid reloading the same profile repeatedly
        if (!forceRefresh && _uiState.value.userProfile?.userId == userId) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = !forceRefresh,
                isRefreshingFriends = forceRefresh,
                error = null
            )

            // Faza C (16. avgust 2026, korisnički zahtev "da deluje momentalno pa se pozadinski
            // sa backom to uradi... i offline stvari") - profil/prijatelji/pending zahtevi/feed
            // aktivnosti/taste profil su ranije uvek čekali mrežni round-trip pre prvog prikaza.
            // Sad se prvo čita Room keš (instant, radi i offline) - mrežni pozivi ispod i dalje idu
            // u pozadini radi osvežavanja/potvrde, `_uiState` se prepisuje svežijim podatkom kad
            // stigne.
            _uiState.value = _uiState.value.copy(
                userProfile = userRepository.observeCachedUserProfile(userId).first() ?: _uiState.value.userProfile,
                friends = userRepository.observeCachedFriends(userId).first().ifEmpty { _uiState.value.friends },
                friendActivity = userRepository.observeCachedFriendActivity(userId).first().ifEmpty { _uiState.value.friendActivity },
                pendingRequests = userRepository.observeCachedPendingRequests(userId).first().ifEmpty { _uiState.value.pendingRequests },
                sentRequests = userRepository.observeCachedSentRequests(userId).first().ifEmpty { _uiState.value.sentRequests },
                tasteProfile = userRepository.observeCachedTasteProfile(userId).first().ifEmpty { _uiState.value.tasteProfile }
            )

            try {
                // Svih pet poziva su nezavisna - paralelno umesto sekvencijalno, inače se vreme
                // čekanja sabira (5x round-trip umesto 1x). (Ranije je ovde bio i šesti poziv,
                // getUserSettings - uklonjen jer backend nikad nije imao /settings rutu, pa je
                // svaki poziv garantovano padao; tema je sad čisto lokalna, videti ThemePreferences.)
                val profileDeferred = async { userRepository.getUserProfile(userId) }
                val friendsDeferred = async { userRepository.getFriends(userId) }
                val activityDeferred = async { userRepository.getFriendActivity(userId) }
                val pendingDeferred = async { userRepository.getPendingFriendRequests(userId) }
                val sentDeferred = async { userRepository.getSentFriendRequests(userId) }
                val tasteProfileDeferred = async { userRepository.getTasteProfile(userId) }

                val profileResult = profileDeferred.await()
                val friendsResult = friendsDeferred.await()
                val activityResult = activityDeferred.await()
                val pendingResult = pendingDeferred.await()
                val sentResult = sentDeferred.await()
                val tasteProfileResult = tasteProfileDeferred.await()

                // Isti bugfix kao u updateProfile() - svaki svež profil sa mreže osvežava i
                // SessionManager keš, da se već postojeće zastarele sesije (npr. avatar promenjen
                // pre nego što je ovaj fix postojao) same isprave čim se profil jednom uspešno učita.
                profileResult.getOrNull()?.let { fresh ->
                    sessionManager.updateCachedIdentity(fresh.username, fresh.avatar)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshingFriends = false,
                    userProfile = profileResult.getOrNull() ?: _uiState.value.userProfile,
                    friends = friendsResult.getOrNull() ?: _uiState.value.friends,
                    friendActivity = activityResult.getOrNull() ?: _uiState.value.friendActivity,
                    pendingRequests = pendingResult.getOrNull() ?: _uiState.value.pendingRequests,
                    sentRequests = sentResult.getOrNull() ?: _uiState.value.sentRequests,
                    tasteProfile = tasteProfileResult.getOrNull() ?: _uiState.value.tasteProfile
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshingFriends = false,
                    error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri učitavanju profila")
                )
            }
        }
    }

    /** Swipe-down na Prijatelji tabu - videti napomenu na loadProfile(forceRefresh). */
    fun refreshFriends() {
        loadProfile(forceRefresh = true)
    }

    fun toggleDarkMode() {
        themePreferences.toggleDarkMode()
    }
}
