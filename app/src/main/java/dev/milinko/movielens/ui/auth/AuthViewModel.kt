package dev.milinko.movielens.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.auth.AuthRepository
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.AuthApi
import dev.milinko.movielens.data.remote.AuthFirebaseRequest
import dev.milinko.movielens.data.remote.UserDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val authApi: AuthApi,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

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
                _uiState.value = AuthUiState.Error(e.message ?: "Nepoznata greška")
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
