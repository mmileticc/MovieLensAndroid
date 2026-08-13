package dev.milinko.movielens.ui.friendprofile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.user.FriendProfileResponse
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendProfileUiState(
    val profile: FriendProfileResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FriendProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val friendId: String = checkNotNull(savedStateHandle["friendId"])

    private val _uiState = MutableStateFlow(FriendProfileUiState())
    val uiState: StateFlow<FriendProfileUiState> = _uiState.asStateFlow()

    fun loadProfile() {
        val myUserId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            userRepository.getFriendProfile(myUserId, friendId)
                .onSuccess { profile ->
                    _uiState.value = _uiState.value.copy(isLoading = false, profile = profile)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Greška pri učitavanju profila"
                    )
                }
        }
    }
}
