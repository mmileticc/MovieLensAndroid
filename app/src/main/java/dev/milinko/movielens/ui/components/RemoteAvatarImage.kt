package dev.milinko.movielens.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.milinko.movielens.data.pref.UrlStorage
import dev.milinko.movielens.data.remote.image.DataUriImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bugfix (E2.5, 2. septembar 2026, feedback testera - "sporo skrolovanje, možda zbog sporijeg
 * učitavanja slike") - deljen keš dekodiranih bitmapa za `data:image/...;base64,...` avatare, po
 * ceo proces aplikacije. RANIJE se dekodiranje radilo iznova pri SVAKOM ulasku stavke liste u
 * prikaz (svaki put kad Lazy lista reciklira composable za istog korisnika tokom skrola - isti
 * avatar bi se dekodirao desetine puta u jednoj sesiji skrolovanja). Malo (20 stavki) - avatari su
 * mali dekodirani bitmapi, ne treba mnogo da se pokrije tipičan feed/listu prijatelja.
 */
private val dataUriBitmapCache = LruCache<String, Bitmap>(20)

/**
 * Zamena za `AsyncImage(model = avatarUrl, ...)` svuda gde avatar polje može biti i "obična"
 * https:// slika (Google avatar) I novi `data:image/...;base64,...` URI (Uredi profil - videti
 * `util/AvatarImage.kt` kompresiju pri slanju). Coil 2.x nema ugrađenu podršku za `data:` scheme,
 * pa se taj slučaj dekodira direktno u Bitmap (`DataUriImage.decode`) i prikazuje preko `Image`,
 * mimoilazeći Coil samo za taj jedan slučaj - obični URL-ovi i dalje idu kroz `AsyncImage`/Coil
 * (keširanje, placeholder, itd.) potpuno nepromenjeno.
 *
 * Bugfix (E2.5, 2. septembar 2026) - RANIJE je `DataUriImage.decode(url)` (Base64 dekodiranje +
 * `BitmapFactory.decodeByteArray`, CPU-teška operacija) bio pozivan SINHRONO unutar `remember {}`
 * - to blokira glavnu (UI) nit tokom kompozicije, tačno u trenutku kad stavka liste ulazi u prikaz
 * pri skrolovanju - uzrok "trzanja"/sporog skrola na ekranima sa više avatara (feed aktivnosti
 * prijatelja, lista prijatelja, zahtevi). Sad se dekodiranje radi u `LaunchedEffect` na
 * `Dispatchers.Default` (van glavne niti) i rezultat se čuva u `dataUriBitmapCache` - stavka se
 * kratko prikaže prazna dok traje prvo dekodiranje tog avatara, ali skrol se više ne zaglavljuje, i
 * ponovni ulazak iste stavke u prikaz čita iz keša (instant, bez ponovnog dekodiranja).
 */
@Composable
fun RemoteAvatarImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (url != null && url.startsWith("data:image")) {
        var bitmap by remember(url) { mutableStateOf(dataUriBitmapCache.get(url)) }
        LaunchedEffect(url) {
            if (bitmap == null) {
                val decoded = withContext(Dispatchers.Default) { DataUriImage.decode(url) }
                if (decoded != null) {
                    dataUriBitmapCache.put(url, decoded)
                }
                bitmap = decoded
            }
        }
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        }
    } else {
        val context = LocalContext.current
        // Avatari koje je korisnik sam postavio stižu kao putanja na backendu
        // (/api/users/{id}/avatar?v=...) kad server nema podešen PUBLIC_BASE_URL - dopunjuje se
        // trenutno izabranim backendom.
        val resolvedUrl = remember(url) {
            if (url != null && url.startsWith("/")) UrlStorage.readBaseUrl(context).trimEnd('/') + url else url
        }
        AsyncImage(
            model = ImageRequest.Builder(context).data(resolvedUrl).crossfade(true).build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
