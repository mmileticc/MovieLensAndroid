package dev.milinko.movielens.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import dev.milinko.movielens.data.remote.vision.VisionApi
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

interface VisionRepository {
    suspend fun detect(imageUri: String): VisionSearchResponse
}

@Singleton
class VisionRepositoryImpl @Inject constructor(
    private val visionApi: VisionApi,
    @ApplicationContext private val context: Context
) : VisionRepository {

    override suspend fun detect(imageUri: String): VisionSearchResponse {
        try {
            val file = getFileFromUri(imageUri)
                ?: throw IllegalStateException("Ne mogu pripremiti sliku za upload.")

            val mimeType = contentResolverMimeOrGuess(context, imageUri.toUri()) ?: "image/jpeg"
            val requestFile = file.asRequestBody(mimeType.toMediaType())
            val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

            val response = visionApi.detect(body)
            if (response.isSuccessful) {
                return response.body()
                    ?: throw IllegalStateException("Server je vratio prazan odgovor.")
            }

            val rawError = response.errorBody()?.string().orEmpty()
            val parsedMessage = runCatching {
                val json = JSONObject(rawError)
                json.optString("error").ifBlank { json.optString("message") }
            }.getOrDefault("")

            throw IllegalStateException(
                parsedMessage.takeIf { it.isNotBlank() }
                    ?: "Greška servera (${response.code()})"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(e.toUserFacingMessage(), e)
        }
    }

    private fun Throwable.toUserFacingMessage(): String {
        return when (this) {
            is SocketTimeoutException -> "Vreme je isteklo dok je slika slana. Proveri internet i pokušaj ponovo."
            is UnknownHostException -> "Ne mogu da pronađem server. Proveri internet i adresu backend-a."
            is IOException -> "Mrežna greška tokom slanja slike. Pokušaj ponovo."
            is IllegalStateException -> message?.takeIf { it.isNotBlank() } ?: "Nepoznata greška."
            else -> message?.takeIf { it.isNotBlank() } ?: "Nepoznata greška."
        }
    }

    private fun contentResolverMimeOrGuess(context: Context, uri: Uri): String? {
        var mimeType = context.contentResolver.getType(uri)

        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension != null) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
        }

        return mimeType
    }

    private fun getFileFromUri(uriString: String): File? {
        return try {
            val uri = uriString.toUri()
            val inputStream = context.contentResolver.openInputStream(uri)
            val extension = MimeTypeMap.getFileExtensionFromUrl(uriString) ?: "jpg"
            val file = File(context.cacheDir, "vision_${System.currentTimeMillis()}.$extension")
            val outputStream = FileOutputStream(file)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            file
        } catch (_: Exception) {
            null
        }
    }
}



