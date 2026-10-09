package dev.milinko.movielens.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import dev.milinko.movielens.data.remote.vision.VisionApi
import dev.milinko.movielens.data.remote.vision.VisionSearchResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Recognizes a movie from a photo (poster or film still) via the backend's Gemini vision endpoint. */
interface VisionRepository {
    /**
     * Shrinks and uploads the image at [imageUri] and returns the recognized movie. Throws
     * [TransientServiceException] when Gemini is overloaded (503) and IllegalStateException with
     * a user-facing message for every other failure.
     */
    suspend fun detect(imageUri: String): VisionSearchResponse
}

@Singleton
class VisionRepositoryImpl @Inject constructor(
    private val visionApi: VisionApi,
    @ApplicationContext private val context: Context
) : VisionRepository {

    private companion object {
        // The backend rejects anything over 8MB (see Routing.kt, /chat/vision); aim much lower for
        // a safety margin and a fast upload on mobile data.
        const val MAX_UPLOAD_BYTES = 3 * 1024 * 1024
        // Gemini does not need a full-resolution camera image (often 3000-4000px); 1600px on the
        // longer side is enough to recognize a poster and makes the file much smaller.
        const val MAX_DIMENSION_PX = 1600
    }

    override suspend fun detect(imageUri: String): VisionSearchResponse {
        try {
            // Image processing (two decode passes, EXIF rotation, the JPEG compression loop and
            // the file write) runs on Dispatchers.IO: viewModelScope.launch starts on the main
            // thread, and a full camera image would otherwise freeze the UI noticeably.
            val file = withContext(Dispatchers.IO) { getFileFromUri(imageUri) }
                ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_image_prepare))

            // After compression the file is always JPEG (compressBitmapToFile), whatever the
            // original format was, so there is no need to guess the MIME type.
            val requestFile = file.asRequestBody("image/jpeg".toMediaType())
            val body = MultipartBody.Part.createFormData("image", file.name, requestFile)

            val response = visionApi.detect(body)
            if (response.isSuccessful) {
                return response.body()
                    ?: throw IllegalStateException(dev.milinko.movielens.util.AppLocale.string(dev.milinko.movielens.R.string.error_empty_response))
            }

            // Thrown as HttpException like everywhere else and mapped below; the backend's raw
            // error body is never shown to the user.
            throw HttpException(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // The backend returns 503 when Gemini reports overload or a rate limit (see
            // GeminiService.isGeminiOverloadSignal). That is common enough to deserve its own
            // TransientServiceException, which SearchViewModel shows as a calm "try again shortly"
            // card instead of a red error (see TransientServiceException.kt). Other HTTP errors
            // go through friendlyMessage().
            if (e.code() == 503) {
                throw dev.milinko.movielens.util.TransientServiceException(
                    "Prepoznavanje slike je trenutno preopterećeno (previše zahteva odjednom). Pokušaj ponovo za par trenutaka."
                )
            }
            throw IllegalStateException(dev.milinko.movielens.util.friendlyMessage(e, dev.milinko.movielens.R.string.error_image_analysis_server), e)
        } catch (e: Exception) {
            throw IllegalStateException(e.toUserFacingMessage(), e)
        }
    }

    private fun Throwable.toUserFacingMessage(): String {
        // Only messages the app wrote itself (IllegalStateException) are shown as they are; any
        // other exception type falls back to a generic message so raw technical text never
        // reaches the user.
        return when (this) {
            is SocketTimeoutException -> "Vreme je isteklo dok je slika slana. Proveri internet i pokušaj ponovo."
            is UnknownHostException -> "Ne mogu da pronađem server. Proveri internet i adresu backend-a."
            is IOException -> "Mrežna greška tokom slanja slike. Pokušaj ponovo."
            is IllegalStateException -> message?.takeIf { it.isNotBlank() } ?: "Nepoznata greška. Pokušaj ponovo."
            else -> "Nepoznata greška. Pokušaj ponovo."
        }
    }

    /**
     * Prepares the file for upload: downsample (at most ~1600px on the longer side), EXIF rotation
     * fix, then JPEG compression with decreasing quality until it fits under MAX_UPLOAD_BYTES.
     * Uploading the raw camera bytes (often 8-15MB) would exceed the backend's 8MB limit.
     */
    private fun getFileFromUri(uriString: String): File? {
        return try {
            val uri = uriString.toUri()
            val bitmap = decodeSampledAndRotatedBitmap(uri, MAX_DIMENSION_PX) ?: return null
            val file = File(context.cacheDir, "vision_${System.currentTimeMillis()}.jpg")
            compressBitmapToFile(bitmap, file, MAX_UPLOAD_BYTES)
            bitmap.recycle()
            file
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Two BitmapFactory passes: the first reads only the dimensions (inJustDecodeBounds, cheap) to
     * compute inSampleSize; the second decodes an already reduced image. Decoding at full
     * resolution and scaling afterwards could cause OutOfMemoryError on weaker phones.
     */
    private fun decodeSampledAndRotatedBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        // decodeStream always returns null when inJustDecodeBounds = true (it only fills
        // outWidth/outHeight), so that null must not be treated as a failure. Only a null from
        // openInputStream() means the stream is unavailable.
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { input -> BitmapFactory.decodeStream(input, null, boundsOptions) }

        var sampleSize = 1
        val width = boundsOptions.outWidth
        val height = boundsOptions.outHeight
        if (width > 0 && height > 0) {
            while ((width / sampleSize) > maxDimensionPx || (height / sampleSize) > maxDimensionPx) {
                sampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
        val rawBitmap = decodeStream.use { input -> BitmapFactory.decodeStream(input, null, decodeOptions) }
            ?: return null

        // Camera images often store the orientation as an EXIF tag instead of rotating the pixels;
        // without this many images would reach Gemini sideways or upside down.
        val rotationDegrees = getExifRotationDegrees(uri)
        if (rotationDegrees == 0) return rawBitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated !== rawBitmap) rawBitmap.recycle()
        return rotated
    }

    private fun getExifRotationDegrees(uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * JPEG compression with decreasing quality (90 -> 20, step 10) until the file fits under
     * maxBytes. If it still does not fit at quality 20 (rare after downsampling to 1600px), the
     * smallest version is sent anyway rather than blocking the upload.
     */
    private fun compressBitmapToFile(bitmap: Bitmap, file: File, maxBytes: Int) {
        var quality = 90
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality > 20)

        FileOutputStream(file).use { it.write(bytes) }
    }
}



