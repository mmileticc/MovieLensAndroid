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
 * Compresses a profile picture into a compact `data:image/jpeg;base64,...` URI sent in the profile
 * update body; the backend stores the image in its avatars table and serves it by URL.
 * The downsample + EXIF rotation logic is similar to VisionRepositoryImpl (camera images can be
 * 8-15MB and wrongly rotated) but kept separate on purpose: the target size is much smaller (an
 * avatar is always a small circle) and the output is different, so sharing the code would only
 * complicate both places.
 */
object AvatarImage {
    private const val MAX_DIMENSION_PX = 480
    private const val MAX_JPEG_BYTES = 150 * 1024

    /** Returns `null` if the image cannot be read or decoded (e.g. the user cancelled the picker). */
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
