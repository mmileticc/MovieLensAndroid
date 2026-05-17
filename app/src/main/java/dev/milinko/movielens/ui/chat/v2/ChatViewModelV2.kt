package dev.milinko.movielens.ui.chat.v2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dev.milinko.movielens.data.repository.VisionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VisionUiState(
    val isLoading: Boolean = false,
    val selectedImageUri: String? = null,
    val result: VisionSearchResponse? = null,
    val error: String? = null
)

@HiltViewModel
class ChatViewModelV2 @Inject constructor(
    private val repository: VisionRepository,
    private val urlStorage: UrlStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisionUiState())
    val uiState: StateFlow<VisionUiState> = _uiState.asStateFlow()

    private val _baseUrl = MutableStateFlow(urlStorage.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    fun onImageSelected(uri: String?) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            result = null,
            error = null
        )
    }

    fun updateBaseUrl(newUrl: String) {
        urlStorage.setBaseUrl(newUrl)
        _baseUrl.value = urlStorage.getBaseUrl()
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(result = null, error = null)
    }

    fun detectSelectedImage() {
        val imageUri = _uiState.value.selectedImageUri ?: run {
            _uiState.value = _uiState.value.copy(error = "Prvo odaberi sliku.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val response = repository.detect(imageUri)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    result = response,
                    error = null,
                    selectedImageUri = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Nepoznata greška"
                )
            }
        }
    }
}


