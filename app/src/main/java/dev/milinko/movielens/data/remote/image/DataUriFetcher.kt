package dev.milinko.movielens.data.remote.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Dekodiranje `data:image/...;base64,...` URI-ja (Uredi profil avatar, videti
 * `util/AvatarImage.kt` za kompresiju pri slanju) u `Bitmap` za prikaz.
 *
 * NAPOMENA: Coil 2.x nema ugrađenu podršku za `data:` scheme, a pisanje custom `Fetcher`-a bi
 * zahtevalo Coil-ove interne klase (`ImageSource`) čiji tačan konstruktor u ovoj verziji nije bilo
 * moguće pouzdano potvrditi bez pokretanja kompajlera protiv stvarne biblioteke - rizik od greške
 * koja bi pukla ceo build. Umesto toga, `ui/components/RemoteAvatarImage.kt` koristi ovu funkciju
 * da dekodira data URI-je direktno u Bitmap, mimoilazeći Coil samo za taj slučaj (obični https://
 * avatari i dalje idu kroz AsyncImage/Coil kao i pre).
 */
object DataUriImage {
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
