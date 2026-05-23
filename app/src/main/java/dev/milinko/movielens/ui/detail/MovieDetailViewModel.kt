package dev.milinko.movielens.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.remote.movie.MovieDetail
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MovieDetailUiState(
    val movieDetail: MovieDetail? = null,
    val isLoading: Boolean = false,
    val isInWatchlist: Boolean = false,
    val userRating: Double = 0.0,
    val error: String? = null
)

@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovieDetailUiState())
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    fun loadMovieDetail(tmdbId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            movieRepository.getMovieDetail(tmdbId)
                .onSuccess { detail ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movieDetail = detail
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Greška pri učitavanju filma"
                    )
                }
        }
    }

    fun setUserRating(rating: Double) {
        _uiState.value = _uiState.value.copy(userRating = rating)
    }

    fun toggleWatchlist(userId: String, tmdbId: Int) {
        viewModelScope.launch {
            val isInWatchlist = _uiState.value.isInWatchlist
            if (isInWatchlist) {
                userRepository.removeFromWatchlist(userId, tmdbId)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(isInWatchlist = false)
                    }
            } else {
                userRepository.addToWatchlist(userId, tmdbId)
                    .onSuccess {
                        _uiState.value = _uiState.value.copy(isInWatchlist = true)
                    }
            }
        }
    }
}

