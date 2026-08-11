package dev.milinko.movielens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import kotlinx.coroutines.CancellationException
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
    // Odvojeno od isLoading (koji je za vision detekciju) da ensureTrendingLoaded ne bude
    // slučajno blokiran dok korisnik analizira sliku, i obrnuto.
    val isTrendingLoading: Boolean = false,
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

    /**
     * Poziva se svaki put kad se Home ekran (ponovo) prikaže. HomeViewModel živi koliko i cela
     * sesija (Home je start destinacija), pa je init{} pozvan samo jednom - ako taj prvi poziv
     * ne uspe (npr. hladan start backend-a), lista bi ostala prazna zauvek bez ovoga. Ne dira
     * podatke ako već postoje ili ako je poziv u toku.
     */
    fun ensureTrendingLoaded() {
        if (_uiState.value.trendingMovies.isNotEmpty() || _uiState.value.isTrendingLoading) return
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
            } catch (e: CancellationException) {
                throw e
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
            _uiState.value = _uiState.value.copy(isTrendingLoading = true)
            movieRepository.getTrendingMovies()
                .onSuccess { movies ->
                    _uiState.value = _uiState.value.copy(isTrendingLoading = false, trendingMovies = movies)
                }
                .onFailure {
                    // Ne pišemo u `error` (to polje je vezano za vision UI) - samo gasimo loading
                    // flag da bi ensureTrendingLoaded() mogao da pokuša ponovo sledeći put kad se
                    // korisnik vrati na Home (npr. posle hladnog starta backend-a).
                    _uiState.value = _uiState.value.copy(isTrendingLoading = false)
                }
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(visionResult = null, error = null)
    }
}

