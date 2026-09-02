package dev.milinko.movielens.ui.friendprofile

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.user.FriendProfileResponse
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendProfileUiState(
    val profile: FriendProfileResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    // "Movie Night" dugme ima smisla samo kad gledaš tuđi profil, ne sopstveni.
    val isSelf: Boolean = false,
    // E1.5 dorada - dok se šalje/otkazuje zahtev sa OVOG ekrana, dugme se privremeno onemogući
    // (sprečava dupli tap dok zahtev leti, isti princip kao ProfileViewModel).
    val isRequestActionInFlight: Boolean = false
)

@HiltViewModel
class FriendProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val friendId: String = checkNotNull(savedStateHandle["friendId"])

    private val _uiState = MutableStateFlow(
        FriendProfileUiState(isSelf = sessionManager.getUserId() == friendId)
    )
    val uiState: StateFlow<FriendProfileUiState> = _uiState.asStateFlow()

    private var requestActionJob: Job? = null

    fun loadProfile() {
        val myUserId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Faza C (16. avgust 2026) - instant prikaz iz Room keša (radi i offline) dok mreža
            // osvežava u pozadini, isti obrazac kao ProfileViewModel/MovieDetailViewModel.
            userRepository.observeCachedFriendProfile(myUserId, friendId).first()?.let { cached ->
                _uiState.value = _uiState.value.copy(profile = cached)
            }

            userRepository.getFriendProfile(myUserId, friendId)
                .onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(isLoading = false, profile = profile)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = dev.milinko.movielens.util.friendlyMessage(e, "Greška pri učitavanju profila")
                    )
                }
        }

        // Prefetch movienight predloga u pozadini čim se otvori profil prijatelja - dok korisnik
        // gleda profil/odgledane filmove, MovieNightViewModel kasnije samo čita iz keša (vidi
        // UserRepository.prefetchMovieNightSuggestions), umesto da čeka mrežu tek na klik dugmeta.
        // Nema uticaja na uiState/loading - potpuno tiho, ne blokira ni ne signalizira ništa ako padne.
        if (myUserId != friendId) {
            viewModelScope.launch {
                userRepository.prefetchMovieNightSuggestions(myUserId, friendId)
            }
        }
    }

    /**
     * E1.5 dorada (2. septembar 2026, feedback testera - "nemogućnost pregleda profila pre slanja
     * zahteva") - sad se do ovog ekrana može stići i za korisnika koji NIJE prijatelj (ograničen
     * preview, videti FriendsRouting.kt na backendu), pa ekran treba i dugme za slanje zahteva
     * direktno odavde, ne samo sa Profile/Pretraga ekrana. Optimistički menja `friendshipStatus`
     * na profilu, isti obrazac kao ProfileViewModel.sendFriendRequest.
     */
    fun sendFriendRequest() {
        // Bugfix (2. septembar 2026, feedback - "posalji/otkazi rapidno, dodaj posle radi sporo i
        // nikako da osvezi") - RANIJE se prethodni let (in-flight) posao OTKAZIVAO da bi se odmah
        // pokrenuo suprotni. Problem: Retrofit suspend poziv već može biti poslat na spor Render
        // backend pre nego što lokalni cancel() stigne da ga prekine - server ga onda ipak
        // obradi, dok klijent (zbog cancel-a) NIKAD ne dobije onSuccess/onFailure da sinhronizuje
        // UI/keš sa stvarnim stanjem. Rezultat: klijent i server se rasinhronizuju posle par brzih
        // uzastopnih tapova, i dugme ostaje "zaglavljeno" pogrešno dok se ekran ne osveži iz nule.
        // Fix: dok je akcija u letu, novi tap se IGNORIŠE (dugme je i vizuelno onemogućeno preko
        // isRequestActionInFlight) umesto da otkazuje/preklapa poziv - svaka akcija mora da se
        // završi (uspešno ili ne) pre sledeće, pa se stanje uvek na kraju ispravno sinhronizuje.
        if (_uiState.value.isRequestActionInFlight) return
        val myUserId = sessionManager.getUserId() ?: return
        val previousProfile = _uiState.value.profile ?: return
        val updatedProfile = previousProfile.copy(friendshipStatus = "PENDING_SENT")
        _uiState.value = _uiState.value.copy(profile = updatedProfile, isRequestActionInFlight = true)
        requestActionJob = viewModelScope.launch {
            // Bugfix (2. septembar 2026) - piše i u Room keš, ne samo u _uiState, da ponovno
            // otvaranje ovog profila (sveža ViewModel instanca) odmah pokaže "Otkaži zahtev"
            // umesto starog keš zapisa dok mreža (spor Render cold-start) ne stigne da ispravi.
            // Videti napomenu uz cacheFriendProfile u UserRepository.kt.
            userRepository.cacheFriendProfile(myUserId, friendId, updatedProfile)
            userRepository.addFriend(myUserId, friendId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isRequestActionInFlight = false)
                }
                .onFailure { e ->
                    Log.w("FriendProfileViewModel", "sendFriendRequest nije uspeo: ${e.message}")
                    _uiState.value = _uiState.value.copy(profile = previousProfile, isRequestActionInFlight = false)
                    userRepository.cacheFriendProfile(myUserId, friendId, previousProfile)
                }
        }
    }

    /** Otkazuje već poslat zahtev - isti DELETE endpoint kao unfriend/reject (videti ProfileViewModel). */
    fun cancelFriendRequest() {
        // Bugfix (2. septembar 2026) - videti napomenu u sendFriendRequest() - isti "ignoriši dok
        // je u letu" princip umesto cancel-i-pa-pokreni-suprotno.
        if (_uiState.value.isRequestActionInFlight) return
        val myUserId = sessionManager.getUserId() ?: return
        val previousProfile = _uiState.value.profile ?: return
        val updatedProfile = previousProfile.copy(friendshipStatus = "NONE")
        _uiState.value = _uiState.value.copy(profile = updatedProfile, isRequestActionInFlight = true)
        requestActionJob = viewModelScope.launch {
            userRepository.cacheFriendProfile(myUserId, friendId, updatedProfile)
            userRepository.removeFriend(myUserId, friendId)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isRequestActionInFlight = false)
                }
                .onFailure { e ->
                    Log.w("FriendProfileViewModel", "cancelFriendRequest nije uspeo: ${e.message}")
                    _uiState.value = _uiState.value.copy(profile = previousProfile, isRequestActionInFlight = false)
                    userRepository.cacheFriendProfile(myUserId, friendId, previousProfile)
                }
        }
    }
}
