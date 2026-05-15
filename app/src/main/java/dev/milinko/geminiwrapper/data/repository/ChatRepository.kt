package dev.milinko.geminiwrapper.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import dev.milinko.geminiwrapper.data.local.ChatDao
import dev.milinko.geminiwrapper.data.local.ChatMessage
import dev.milinko.geminiwrapper.data.remote.ChatApiService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

interface ChatRepository {
    fun getMessages(): Flow<List<ChatMessage>>
    suspend fun sendMessage(text: String, imageUri: String? = null)
    suspend fun clearChat()
}

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val apiService: ChatApiService,
    @ApplicationContext private val context: Context
) : ChatRepository {

    companion object {
        private const val MAX_FILE_SIZE = 10 * 1024 * 1024 // 10 MB
    }

    override fun getMessages(): Flow<List<ChatMessage>> = chatDao.getAllMessages()

    override suspend fun sendMessage(text: String, imageUri: String?) {
        // 1. Sačuvaj korisničku poruku u Room
        val userMessage = ChatMessage(text = text, imageUri = imageUri, isUser = true)
        chatDao.insertMessage(userMessage)

        try {
            val responseText = if (imageUri != null) {
                // Slanje sa slikom
                val uri = Uri.parse(imageUri)
                
                // Provera veličine fajla
                val fileSize = getFileSize(uri)
                if (fileSize > MAX_FILE_SIZE) {
                    throw Exception("Fajl je prevelik. Maksimalna dozvoljena veličina je 10 MB.")
                }

                val file = getFileFromUri(imageUri)
                if (file != null) {
                    val mimeType = contentResolverMimeOrGuess(context, uri) ?: "image/jpeg"
                    val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("image", file.name, requestFile)
                    val messageBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
                    apiService.sendMessageWithImage(messageBody, body)
                } else {
                    apiService.sendMessage(text)
                }
            } else {
                // Slanje samo teksta
                apiService.sendMessage(text)
            }

            // 3. Sačuvaj AI odgovor u Room
            val aiMessage = ChatMessage(text = responseText, isUser = false)
            chatDao.insertMessage(aiMessage)
        } catch (e: Exception) {
            val errorMessage = ChatMessage(text = "Greška: ${e.localizedMessage}", isUser = false)
            chatDao.insertMessage(errorMessage)
        }
    }

    private fun getFileSize(uri: Uri): Long {
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
            it.length
        } ?: 0L
    }

    fun detectMimeFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return "image/jpeg"
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return "image/png"
        }
        // WebP: RIFF .... WEBP
        if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        // HEIC: .... ftypheic or ftypheix
        val ftyp = String(bytes.sliceArray(4..11))
        if (ftyp.contains("ftypheic") || ftyp.contains("ftypheix") || ftyp.contains("ftypmif1")) {
            return "image/heic"
        }
        
        return null
    }

    fun contentResolverMimeOrGuess(context: Context, uri: Uri): String? {
        // 1. Probaj preko ContentResolver
        var mimeType = context.contentResolver.getType(uri)
        
        // 2. Ako ne uspe, probaj preko ekstenzije
        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
        }
        
        // 3. Ako i dalje ne uspe, probaj preko bajtova
        if (mimeType == null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(12)
                    if (input.read(buffer) != -1) {
                        mimeType = detectMimeFromBytes(buffer)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
        
        return mimeType
    }

    private fun getFileFromUri(uriString: String): File? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val extension = MimeTypeMap.getFileExtensionFromUrl(uriString) ?: "jpg"
            val file = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.$extension")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun clearChat() {
        chatDao.clearChat()
    }
}
