package dev.milinko.movielens.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.remote.user.UserProfile
import dev.milinko.movielens.data.remote.user.UserSettings
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.data.remote.user.FriendActivity
import dev.milinko.movielens.data.repository.UserRepository
import dev.milinko.movielens.data.pref.UrlStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val userProfile: UserProfile? = null,
    val userSettings: UserSettings? = null,
    val friends: List<FriendInfo> = emptyList(),
    val friendActivity: List<FriendActivity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val urlStorage: UrlStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    // Expose base URL from UrlStorage so UI can edit backend address during testing
    private val _baseUrl = MutableStateFlow(urlStorage.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    // One-shot UI events (toasts, messages)
    private val _events = MutableSharedFlow<ProfileUiEvent>()
    val events = _events.asSharedFlow()

    sealed class ProfileUiEvent {
        data class ShowMessage(val message: String) : ProfileUiEvent()
    }

    fun updateBaseUrl(newUrl: String) {
        viewModelScope.launch {
            try {
                urlStorage.setBaseUrl(newUrl)
                _baseUrl.value = urlStorage.getBaseUrl()
                _events.emit(ProfileUiEvent.ShowMessage("Backend URL sačuvan. Restart app-a da bi promena stupila na snagu."))
            } catch (e: Exception) {
                _events.emit(ProfileUiEvent.ShowMessage("Greška pri čuvanju URL-a: ${e.message}"))
            }
        }
    }

    fun loadProfile(userId: String) {
        // avoid reloading the same profile repeatedly
        if (_uiState.value.userProfile?.userId == userId) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val profileResult = userRepository.getUserProfile(userId)
                if (profileResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(userProfile = profileResult.getOrNull())
                }

                val settingsResult = userRepository.getUserSettings(userId)
                if (settingsResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(userSettings = settingsResult.getOrNull())
                }

                val friendsResult = userRepository.getFriends(userId)
                if (friendsResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(friends = friendsResult.getOrNull() ?: emptyList())
                }

                val activityResult = userRepository.getFriendActivity(userId)
                if (activityResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(friendActivity = activityResult.getOrNull() ?: emptyList())
                }

                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Greška pri učitavanju profila")
            }
        }
    }

    fun updateSettings(userId: String, settings: UserSettings) {
        viewModelScope.launch {
            userRepository.updateUserSettings(userId, settings)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(userSettings = it)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(error = e.message ?: "Greška pri ažuriranju postavki")
                }
        }
    }

    fun toggleDarkMode(userId: String) {
        val currentSettings = _uiState.value.userSettings
        if (currentSettings != null) {
            val updated = currentSettings.copy(darkMode = !currentSettings.darkMode)
            updateSettings(userId, updated)
        }
    }
}


