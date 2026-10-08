package dev.milinko.movielens.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.auth.AuthRepository
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.push.PushTokenManager
import dev.milinko.movielens.data.remote.AuthApi
import dev.milinko.movielens.data.remote.AuthFirebaseRequest
import dev.milinko.movielens.data.remote.UserDto
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.util.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val movieRepository: MovieRepository,
    private val urlStorage: UrlStorage,
    private val pushTokenManager: PushTokenManager
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
                    pushTokenManager.syncAsync()
                } else {
                    _uiState.value = AuthUiState.Error(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_sign_in_no_token))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Faza A bugfix (16. avgust 2026) - exchangeFirebaseToken NIJE umotan u
                // Response<T>, pa Retrofit ovde direktno baca IOException (nema konekcije/timeout)
                // ili HttpException (5xx sa backenda) - bez friendlyMessage() korisnik bi na Sign
                // In ekranu video sirov tehnički tekst poput "timeout" umesto razumljive poruke.
                _uiState.value = AuthUiState.Error(friendlyMessage(e, dev.milinko.movielens.R.string.error_sign_in_failed))
            }
        }
    }

    /**
     * Google deo prijave nije uspeo (pre nego što dođemo do backend-a) - ranije se to samo
     * logovalo, pa bi se Google prozor zatvorio i ništa vidljivo se ne bi desilo.
     */
    fun reportGoogleSignInFailure(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun signOut() {
        val userId = sessionManager.getUserId()
        val appToken = sessionManager.getAppToken()
        if (userId != null && appToken != null) pushTokenManager.unregisterAsync(userId, appToken)
        authRepo.signOut()
        sessionManager.clearSession()
        _uiState.value = AuthUiState.Idle
    }

    fun isAlreadySignedIn(): Boolean =
        authRepo.isSignedIn() && sessionManager.isSessionValid()

    // Dorada (17. avgust 2026, korisnička žalba "ne mogu ništa da uradim kad sam izlogovan jer se
    // backend ruta vidi samo kad si ulogovan") - urediva backend ruta je ranije postojala SAMO na
    // Profile → Podešavanja tabu, koji je (dorada #2, task #160) sad zaključan iza prijave. Bez
    // ovoga, korisnik na pogrešnom backend URL-u (npr. promenjen IP na lokalnoj mreži) nije imao
    // NIKAKAV način da ga promeni - Sign In je jedini ekran uvek dostupan izlogovanom korisniku.
    // Reciklira isti UrlStorage/SettingsDialog koji Profile već koristi (videti ProfileViewModel) -
    // ista podešavanja, samo drugi ulaz, nema duplog izvora istine.
    private val _baseUrl = MutableStateFlow(urlStorage.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _savedRoutes = MutableStateFlow(urlStorage.getSavedRoutes())
    val savedRoutes: StateFlow<List<String>> = _savedRoutes.asStateFlow()

    private fun refreshRoutesState() {
        _baseUrl.value = urlStorage.getBaseUrl()
        _savedRoutes.value = urlStorage.getSavedRoutes()
    }

    fun updateBaseUrl(newUrl: String) {
        urlStorage.setBaseUrl(newUrl)
        refreshRoutesState()
    }

    fun addSavedRoute(url: String) {
        urlStorage.addRoute(url)
        refreshRoutesState()
    }

    fun removeSavedRoute(url: String) {
        urlStorage.removeRoute(url)
        refreshRoutesState()
    }
}

sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class SignedIn(val user: UserDto) : AuthUiState
    data class Error(val message: String) : AuthUiState
}
