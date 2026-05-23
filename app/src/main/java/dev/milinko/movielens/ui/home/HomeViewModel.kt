package dev.milinko.movielens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val selectedImageUri: String? = null,
    val visionResult: VisionSearchResponse? = null,
    val trendingMovies: List<MovieSearchResult> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val visionRepository: VisionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTrendingMovies()
    }

    fun onImageSelected(uri: String?) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            visionResult = null,
            error = null
        )
    }

    fun detectImage() {
        val imageUri = _uiState.value.selectedImageUri ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = visionRepository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    visionResult = response,
                    selectedImageUri = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Greška pri analizi slike"
                )
            }
        }
    }

    private fun loadTrendingMovies() {
        viewModelScope.launch {
            movieRepository.getTrendingMovies().onSuccess { movies ->
                _uiState.value = _uiState.value.copy(trendingMovies = movies)
            }
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(visionResult = null, error = null)
    }
}

