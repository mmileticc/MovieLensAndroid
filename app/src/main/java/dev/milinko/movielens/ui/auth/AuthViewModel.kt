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

/**
 * Sign-in flow: Google -> Firebase -> backend session exchange, sign-out, the decorative poster
 * background and (in debug builds) the backend URL choice on the Sign In screen.
 */
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

    // Poster collage for the Sign In background: purely decorative and independent of the sign-in
    // state (public /trending route, no token). If the call fails the list stays empty and the screen
    // shows a plain gradient - no error banner, no retry, and the sign-in flow is never affected.
    //
    // Not loaded in init{}: AuthViewModel is also used on ProfileScreen (isSignedIn check), where the
    // posters are not needed. Only SignInScreen calls loadBackgroundPostersIfNeeded(), and
    // isLoadingPosters prevents duplicate calls.
    private val _backgroundPosters = MutableStateFlow<List<String>>(emptyList())
    val backgroundPosters: StateFlow<List<String>> = _backgroundPosters
    private var didLoadBackgroundPosters = false

    /** Loads the background posters once (see the note above). */
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
                // exchangeFirebaseToken is not wrapped in Response<T>, so Retrofit throws IOException
                // (no connection/timeout) or HttpException (5xx) directly; friendlyMessage() turns
                // them into a readable message instead of raw technical text.
                _uiState.value = AuthUiState.Error(friendlyMessage(e, dev.milinko.movielens.R.string.error_sign_in_failed))
            }
        }
    }

    /**
     * The Google part of sign-in failed (before the backend is reached); shown as an error so the
     * Google window does not just close with nothing visible happening.
     */
    fun reportGoogleSignInFailure(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    /** Signs out of Firebase and the backend session and unregisters this device's push token. */
    fun signOut() {
        val userId = sessionManager.getUserId()
        val appToken = sessionManager.getAppToken()
        if (userId != null && appToken != null) pushTokenManager.unregisterAsync(userId, appToken)
        authRepo.signOut()
        sessionManager.clearSession()
        _uiState.value = AuthUiState.Idle
    }

    /** True if both the Firebase user and the backend session exist. */
    fun isAlreadySignedIn(): Boolean =
        authRepo.isSignedIn() && sessionManager.isSessionValid()

    // Backend URL for the developer dialog on the Sign In screen - the only screen always available
    // when signed out, so a wrong URL (e.g. a changed local IP) can be fixed without signing in. Uses
    // the same UrlStorage as the Profile settings.
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

/** State of the sign-in flow. */
sealed interface AuthUiState {
    object Idle : AuthUiState
    object Loading : AuthUiState
    data class SignedIn(val user: UserDto) : AuthUiState
    data class Error(val message: String) : AuthUiState
}
