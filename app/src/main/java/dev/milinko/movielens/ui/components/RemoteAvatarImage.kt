package dev.milinko.movielens.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.data.remote.image.DataUriImage

/**
 * Zamena za `AsyncImage(model = avatarUrl, ...)` svuda gde avatar polje može biti i "obična"
 * https:// slika (Google avatar) I novi `data:image/...;base64,...` URI (Uredi profil - videti
 * `util/AvatarImage.kt` kompresiju pri slanju). Coil 2.x nema ugrađenu podršku za `data:` scheme,
 * pa se taj slučaj dekodira direktno u Bitmap (`DataUriImage.decode`) i prikazuje preko `Image`,
 * mimoilazeći Coil samo za taj jedan slučaj - obični URL-ovi i dalje idu kroz `AsyncImage`/Coil
 * (keširanje, placeholder, itd.) potpuno nepromenjeno.
 */
@Composable
fun RemoteAvatarImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (url != null && url.startsWith("data:image")) {
        val bitmap = remember(url) { DataUriImage.decode(url) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
