package dev.milinko.movielens.ui.movienight

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.user.MovieSuggestion
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieNightUiState(
    val suggestions: List<MovieSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MovieNightViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val friendId: String = checkNotNull(savedStateHandle["friendId"])

    private val _uiState = MutableStateFlow(MovieNightUiState())
    val uiState: StateFlow<MovieNightUiState> = _uiState.asStateFlow()

    fun loadSuggestions() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            userRepository.getMovieNightSuggestions(userId, friendId)
                .onSuccess { suggestions ->
                    _uiState.value = _uiState.value.copy(isLoading = false, suggestions = suggestions)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Greška pri učitavanju predloga"
                    )
                }
        }
    }
}
