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
 * Process-wide cache of decoded `data:image/...;base64,...` avatars, so a list item scrolling back
 * into view does not decode the same avatar again. Small (20 entries): avatars are tiny bitmaps.
 */
private val dataUriBitmapCache = LruCache<String, Bitmap>(20)

/**
 * Avatar image that accepts both a regular https:// URL (e.g. a Google avatar or the backend's avatar
 * endpoint) and a `data:image/...;base64,...` URI. Coil 2 cannot load `data:` URIs, so those are
 * decoded with `DataUriImage.decode` and shown with `Image`; regular URLs still go through Coil.
 *
 * Decoding runs in a `LaunchedEffect` on `Dispatchers.Default` (off the main thread) and the result
 * is kept in `dataUriBitmapCache`. Decoding synchronously during composition made scrolling stutter
 * on screens with many avatars.
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
    // Avatars uploaded by the user arrive as a backend path (/api/users/{id}/avatar?v=...) when the
    // server has no PUBLIC_BASE_URL configured; it is completed with the currently selected backend.
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
