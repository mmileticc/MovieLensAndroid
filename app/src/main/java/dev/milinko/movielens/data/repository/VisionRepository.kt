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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
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

    private companion object {
        // Backend odbija sve preko 8MB (videti Routing.kt, /chat/vision) - ciljamo mnogo manje,
        // i zbog sigurnosne margine i da upload bude brz na mobilnom internetu.
        const val MAX_UPLOAD_BYTES = 3 * 1024 * 1024
        // TMDB/Gemini ne treba full-res kameru (često 3000-4000px) - 1600px duža strana je i
        // dalje sasvim dovoljno da Gemini prepozna plakat, a drastično smanjuje veličinu fajla.
        const val MAX_DIMENSION_PX = 1600
    }

    override suspend fun detect(imageUri: String): VisionSearchResponse {
        try {
            val file = getFileFromUri(imageUri)
                ?: throw IllegalStateException("Ne mogu pripremiti sliku za upload.")

            // Fajl je posle kompresije uvek JPEG (compressBitmapToFile), bez obzira na originalni
            // format ulazne slike - ne treba više nagađanje MIME tipa iz content resolver-a.
            val requestFile = file.asRequestBody("image/jpeg".toMediaType())
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

    /**
     * Priprema fajl za upload - downsample (max ~1600px duža strana), EXIF rotaciona korekcija,
     * pa JPEG kompresija sa opadajućim kvalitetom dok ne stane pod MAX_UPLOAD_BYTES.
     *
     * RANIJE (bug): ova funkcija je samo kopirala sirove bajtove sa content:// URI-ja, bez ikakve
     * obrade - moderna kamera slika lako ume da bude 8-15MB, što je backend odbijao sa "slika
     * prevelika" (8MB limit, videti Routing.kt /chat/vision). Otuda greške koje su se ranije
     * javljale pri skeniranju.
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
     * Dva prolaza kroz BitmapFactory: prvi samo čita dimenzije (inJustDecodeBounds, jeftino - ne
     * učitava piksele), da se izračuna inSampleSize za downsample; drugi stvarno dekodira sliku
     * već smanjenu, umesto da se puna rezolucija učita u memoriju pa tek onda skalira (to bi na
     * starijim/slabijim telefonima moglo da izazove OutOfMemoryError za velike kamera slike).
     */
    private fun decodeSampledAndRotatedBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        // VAŽNO: BitmapFactory.decodeStream UVEK vraća null kad je inJustDecodeBounds=true (to je
        // normalno - samo puni outWidth/outHeight, ne alocira piksele). Zato se null rezultat tog
        // poziva ne sme koristiti kao signal da je otvaranje stream-a palo - to je bio bug (`?:
        // return null` je hvatao baš taj očekivani null i prekidao celu funkciju na svaki poziv).
        // Sad se posebno proverava samo da li openInputStream() vrati null (stream nedostupan).
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

        // Kamera slike često čuvaju orientation kao EXIF tag umesto da stvarno rotiraju piksele -
        // bez ovoga bi mnoge slike stigle na Gemini "na bok" ili naopako.
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
     * JPEG kompresija sa opadajućim kvalitetom (90 -> 20, korak 10) dok fajl ne stane pod
     * maxBytes. U najgorem slučaju (kvalitet već pao na 20 a slika i dalje prevelika - retko posle
     * downsample-a na 1600px) šalje se poslednja (najmanja) verzija, ne blokira upload.
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



