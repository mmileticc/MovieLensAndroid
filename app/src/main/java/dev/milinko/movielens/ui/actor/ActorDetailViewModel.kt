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

data class ActorDetailUiState(
    val person: PersonDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * E2.3 (2. septembar 2026) - stranica glumca. Namerno prost, bez Room keša/offline podrške
 * (za razliku od MovieDetailViewModel) - podaci o glumcu su čist TMDB proxy prikaz bez ličnih
 * korisničkih akcija (nema watchlist/ocena/komentar ovde), isti obim kao npr. showtimes deo
 * Movie Detail-a pre nego što je dobio Room keš.
 */
@HiltViewModel
class ActorDetailViewModel @Inject constructor(
    private val movieRepository: MovieRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActorDetailUiState())
    val uiState: StateFlow<ActorDetailUiState> = _uiState.asStateFlow()

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
