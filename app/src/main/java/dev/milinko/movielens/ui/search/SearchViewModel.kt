package dev.milinko.movielens.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.VisionRepository
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val searchQuery: String = "",
    // Odvojeno od isAnalyzingImage - deljeno "isLoading" polje je značilo da kucanje teksta dok je
    // slika izabrana zna da prikaže "Analiziram sliku..." dugme iako se ne dešava nikakva analiza.
    val isSearching: Boolean = false,
    val isAnalyzingImage: Boolean = false,
    val selectedImageUri: String? = null,
    val searchResults: List<MovieSearchResult> = emptyList(),
    val visionResult: VisionSearchResponse? = null,
    val error: String? = null
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val movieRepository: MovieRepository,
    private val visionRepository: VisionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Debounce posao za pretragu dok korisnik kuca - svaki novi karakter otkazuje prethodni.
    private var searchJob: Job? = null

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MIN_QUERY_LENGTH = 2
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), error = null)
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            performSearch(trimmed)
        }
    }

    /** Trenutna pretraga na Enter/IME search akciju - preskače debounce, traži odmah. */
    fun searchMovies() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch { performSearch(query) }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isSearching = true, error = null)
        movieRepository.searchMovies(query)
            .onSuccess { results ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = results
                )
            }
            .onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.message ?: "Greška pri pretrazi"
                )
            }
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
            _uiState.value = _uiState.value.copy(isAnalyzingImage = true, error = null)
            try {
                val response = visionRepository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    visionResult = response,
                    selectedImageUri = null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAnalyzingImage = false,
                    error = e.message ?: "Greška pri analizi slike"
                )
            }
        }
    }

    fun clearResults() {
        _uiState.value = _uiState.value.copy(
            searchResults = emptyList(),
            visionResult = null,
            searchQuery = "",
            error = null
        )
    }
}

