package dev.milinko.movielens.data.remote.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Decodes a `data:image/...;base64,...` URI into a Bitmap. Coil 2 cannot load `data:` URIs, so
 * RemoteAvatarImage uses this for avatars that are still data URIs (e.g. a freshly picked picture
 * shown before the upload finishes); regular URLs go through Coil.
 */
object DataUriImage {
    /** The decoded bitmap, or null if the URI is not valid base64 image data. */
    fun decode(dataUri: String): Bitmap? {
        return try {
            val base64 = dataUri.substringAfter(',', missingDelimiterValue = "")
            if (base64.isBlank()) return null
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
