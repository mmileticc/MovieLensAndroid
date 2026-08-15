package dev.milinko.movielens.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/**
 * Kompresija profilne slike u kompaktan `data:image/jpeg;base64,...` URI koji se šalje direktno u
 * PUT /api/users/{userId}/profile telu i čuva u `users.avatar_url` (text kolona, bez potrebe za
 * posebnim file storage-om - van obima diplomskog da se uvodi npr. Firebase Storage/S3 samo za
 * ovo). Downsample+EXIF-rotaciona logika je namerno slična `VisionRepositoryImpl` (ista lekcija -
 * kamera slike znaju biti 8-15MB i pogrešno rotirane), ali je ovde samostalna kopija umesto deljene
 * funkcije - manja ciljna dimenzija (avatar je uvek mali krug, ne treba mu 1600px) i drugačiji
 * izlazni format (base64 string, ne fajl za multipart upload), pa bi deljenje samo zakomplikovalo
 * oba mesta bez stvarne uštede koda.
 */
object AvatarImage {
    private const val MAX_DIMENSION_PX = 480
    private const val MAX_JPEG_BYTES = 150 * 1024

    /** Vraća `null` ako slika ne može da se pročita/dekodira (npr. korisnik je otkazao izbor). */
    fun compressToDataUri(context: Context, uri: Uri): String? {
        val bitmap = decodeSampledAndRotatedBitmap(context, uri, MAX_DIMENSION_PX) ?: return null
        val bytes = compressToJpegBytes(bitmap, MAX_JPEG_BYTES)
        bitmap.recycle()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }

    private fun decodeSampledAndRotatedBitmap(context: Context, uri: Uri, maxDimensionPx: Int): Bitmap? {
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

        val rotationDegrees = getExifRotationDegrees(context, uri)
        if (rotationDegrees == 0) return rawBitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        if (rotated !== rawBitmap) rawBitmap.recycle()
        return rotated
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
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

    private fun compressToJpegBytes(bitmap: Bitmap, maxBytes: Int): ByteArray {
        var quality = 90
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality > 20)
        return bytes
    }
}
