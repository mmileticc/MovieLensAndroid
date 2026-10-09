package dev.milinko.movielens.ui.actor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.remote.movie.PersonDetail
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.util.friendlyMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the person page. */
data class ActorDetailUiState(
    val person: PersonDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Person page. Deliberately simple, without a Room cache: it is a plain TMDB view with no personal
 * user actions (no watchlist, ratings or comments here).
 */
@HiltViewModel
class ActorDetailViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActorDetailUiState())
    val uiState: StateFlow<ActorDetailUiState> = _uiState.asStateFlow()

    /** Loads the person's details; failures end up in `error` as a user-facing message. */
    fun loadPerson(personId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            movieRepository.getPersonDetail(personId)
                .onSuccess { person ->
                    _uiState.value = _uiState.value.copy(isLoading = false, person = person)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyMessage(e, dev.milinko.movielens.R.string.error_load_person)
                    )
                }
        }
    }
}
