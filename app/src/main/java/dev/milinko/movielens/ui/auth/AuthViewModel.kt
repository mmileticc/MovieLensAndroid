package dev.milinko.movielens.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.auth.AuthRepository
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.AuthApi
import dev.milinko.movielens.data.remote.AuthFirebaseRequest
import dev.milinko.movielens.data.remote.UserDto
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.util.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    // UI dorada (17. avgust 2026, korisnička žalba "sign in deluje prazno") - poster kolaž
    // pozadina na Sign In ekranu. Namerno POTPUNO odvojeno od uiState/prijave - ovo je čisto
    // dekorativno, javna /trending ruta (ne treba token, ista ruta koju Home koristi za "Popularno").
    // Ako poziv padne (offline, hladan start backenda) lista ostaje prazna i ekran se prikazuje
    // identično kao pre ove dorade (plain gradijent) - nema error banner-a, nema retry dugmeta, ne
    // sme da utiče na sam tok prijave.
    //
    // NAMERNO nije u init{} - AuthViewModel se ponovo koristi i u ProfileScreen.kt (top bar
    // isSignedIn provera preko hiltViewModel()), pa bi init{} tamo pokretao nepotreban mrežni
    // poziv za podatak koji se nigde na Profile ekranu ne koristi. loadBackgroundPostersIfNeeded()
    // poziva samo SignInScreen (LaunchedEffect), a isLoadingPosters flag sprečava duple pozive.
    private val _backgroundPosters = MutableStateFlow<List<String>>(emptyList())
    val backgroundPosters: StateFlow<List<String>> = _backgroundPosters
    private var didLoadBackgroundPosters = false

    fun loadBackgroundPostersIfNeeded() {
        if (didLoadBackgroundPosters) return
        didLoadBackgroundPosters = true
        viewModelScope.launch {
            movieRepository.getTrendingMovies(page = 1).onSuccess { movies ->
                _backgroundPosters.value = movies.mapNotNull { it.posterPath }
            }
        }
    }

    /** Called right after Google Sign-In returns a Google ID token. */
    fun finishGoogleSignIn(googleIdToken: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                // 1. Sign into Firebase with the Google credential
                authRepo.signInWithGoogleIdToken(googleIdToken)

                // 2. Get the Firebase ID token to send to our backend
                val firebaseIdToken = authRepo.getFirebaseIdToken(forceRefresh = false)
                    ?: throw IllegalStateException("Couldn't get Firebase ID token")

                // 3. Exchange Firebase ID token for backend app token
                val resp = authApi.exchangeFirebaseToken(AuthFirebaseRequest(firebaseIdToken))

                // 4. Persist session
                val user = resp.user
                if (resp.appToken != null && user != null) {
                    sessionManager.saveSession(
                        appToken = resp.appToken,
                        userId = user.id,
                        firebaseUid = user.firebaseUid,
                        username = user.username,
                        email = user.email,
                        avatarUrl = user.avatarUrl
                    )
                    _uiState.value = AuthUiState.SignedIn(user)
                } else {
                    _uiState.value = AuthUiState.Error("Backend nije vratio token. Pokušaj ponovo.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Faza A bugfix (16. avgust 2026) - exchangeFirebaseToken NIJE umotan u
                // Response<T>, pa Retrofit ovde direktno baca IOException (nema konekcije/timeout)
                // ili HttpException (5xx sa backenda) - bez friendlyMessage() korisnik bi na Sign
                // In ekranu video sirov tehnički tekst poput "timeout" umesto razumljive poruke.
                _uiState.value = AuthUiState.Error(friendlyMessage(e, "Prijava nije uspela. Pokušaj ponovo."))
            }
        }
    }

    fun signOut() {
        authRepo.signOut()
        sessionManager.clearSession()
        _uiState.value = AuthUiState.Idle
    }

    fun isAlreadySignedIn(): Boolean =
        authRepo.isSignedIn() && sessionManager.isSessionValid()
}

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class SignedIn(val user: UserDto) : AuthUiState
    data class Error(val message: String) : AuthUiState
}
