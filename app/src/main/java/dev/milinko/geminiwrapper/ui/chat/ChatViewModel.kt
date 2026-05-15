package dev.milinko.geminiwrapper.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.geminiwrapper.data.local.ChatMessage
import dev.milinko.geminiwrapper.data.repository.ChatRepository
import dev.milinko.geminiwrapper.data.pref.UrlStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val urlStorage: UrlStorage
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> = repository.getMessages()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedImageUri = MutableStateFlow<String?>(null)
    val selectedImageUri: StateFlow<String?> = _selectedImageUri.asStateFlow()

    private val _baseUrl = MutableStateFlow(urlStorage.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    fun onImageSelected(uri: String?) {
        _selectedImageUri.value = uri
    }

    fun updateBaseUrl(newUrl: String) {
        urlStorage.setBaseUrl(newUrl)
        _baseUrl.value = urlStorage.getBaseUrl()
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && _selectedImageUri.value == null) return
        
        val imageUri = _selectedImageUri.value
        _selectedImageUri.value = null // Reset image after sending (or preparing to send)

        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.sendMessage(text, imageUri)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }
}
