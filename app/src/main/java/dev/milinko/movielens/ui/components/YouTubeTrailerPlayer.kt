package dev.milinko.movielens.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.movie.MovieVideo

/**
 * Trejler(i) filma pušteni u aplikaciji (YouTube IFrame plejer u WebView-u, biblioteka
 * android-youtube-player). Ako ima više videa, red čipova iznad plejera bira koji se prikazuje.
 *
 * - Video se samo "cue"-uje (ne pušta sam) - ne troši podatke dok korisnik ne pritisne play.
 * - Fullscreen: biblioteka nam preda gotov fullscreen View, mi ga dodamo preko celog ekrana u ISTI
 *   prozor (videti FullscreenPlayerOverlay) i prebacimo aktivnost u landscape (MainActivity ima
 *   configChanges za orientation, pa se aktivnost ne kreira ponovo i plejer preživljava rotaciju).
 * - Neki studiji zabranjuju embed (ili plejer iz drugog razloga ne može da pusti video) - tada se
 *   umesto plejera prikazuje "Otvori na YouTube-u".
 */
@Composable
fun YouTubeTrailerPlayer(
    videos: List<MovieVideo>,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) return

    var selectedIndex by remember(videos) { mutableIntStateOf(0) }
    val selected = videos[selectedIndex.coerceIn(videos.indices)]
    // Greška se pamti po video ključu - drugi trejler istog filma možda može da se pusti.
    var failedKeys by remember(videos) { mutableStateOf(emptySet<String>()) }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (videos.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(videos, key = { _, v -> v.key }) { index, video ->
                    FilterChip(
                        selected = index == selectedIndex,
                        onClick = { selectedIndex = index },
                        label = { Text(video.name?.takeIf { it.isNotBlank() } ?: video.type ?: "Trailer", maxLines = 1) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.Black)
        ) {
            if (selected.key in failedKeys) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.movie_detail_trailer_not_embeddable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    AppButton(
                        text = stringResource(R.string.movie_detail_open_youtube),
                        leadingIcon = Icons.Default.PlayArrow,
                        variant = AppButtonVariant.Primary,
                        modifier = Modifier.padding(top = 12.dp),
                        onClick = { context.openYouTube(selected.key) }
                    )
                }
            } else {
                EmbeddedYouTubePlayer(
                    videoKey = selected.key,
                    onError = { failedKeys = failedKeys + selected.key }
                )
            }
        }
    }
}

@Composable
private fun EmbeddedYouTubePlayer(videoKey: String, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Obični MutableState držači (ne `by` delegati) - pišu ih listener-i View-a iz anonimnih klasa.
    val playerState = remember { mutableStateOf<YouTubePlayer?>(null) }
    val fullscreenState = remember { mutableStateOf<Pair<View, () -> Unit>?>(null) }
    // Listener-i su registrovani jednom (pri kreiranju View-a), a moraju da vide aktuelni ključ/callback.
    val currentKey by rememberUpdatedState(videoKey)
    val currentOnError by rememberUpdatedState(onError)

    val playerView = remember {
        YouTubePlayerView(context).apply {
            enableAutomaticInitialization = false
            addFullscreenListener(object : FullscreenListener {
                override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                    fullscreenState.value = fullscreenView to exitFullscreen
                }

                override fun onExitFullscreen() {
                    fullscreenState.value = null
                }
            })
            val options = IFramePlayerOptions.Builder(context)
                .controls(1)
                .fullscreen(1)
                .build()
            initialize(object : AbstractYouTubePlayerListener() {
                override fun onReady(youTubePlayer: YouTubePlayer) {
                    playerState.value = youTubePlayer
                    youTubePlayer.cueVideo(currentKey, 0f)
                }

                override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                    currentOnError()
                }
            }, true, options)
        }
    }

    // Promena izabranog trejlera posle prvog učitavanja (prvi ključ cue-uje onReady).
    LaunchedEffect(videoKey) {
        playerState.value?.cueVideo(videoKey, 0f)
    }

    DisposableEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(playerView)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerView)
            playerView.release()
        }
    }

    AndroidView(factory = { playerView }, modifier = Modifier.fillMaxSize())

    // Dok traje fullscreen, "zakačimo" ovu stavku LazyColumn-a (Movie Detail ekran). Bez ovoga je
    // ekran posle rotacije u landscape (visina ~400dp) prenizak, trejler ispadne iz vidljivog dela
    // liste, LazyColumn ga izbaci iz kompozicije -> onDispose oslobodi plejer i fullscreen se
    // ugasi (potvrđeno logom: onDispose ~100ms posle onConfigurationChanged u landscape).
    // Mora biti PRE FullscreenPlayerOverlay-a ispod - efekti se izvršavaju redom, pa je stavka
    // zakačena pre nego što overlay zatraži promenu orijentacije.
    val pinnableContainer = LocalPinnableContainer.current
    val isFullscreen = fullscreenState.value != null
    DisposableEffect(isFullscreen, pinnableContainer) {
        val pinnedHandle = if (isFullscreen) pinnableContainer?.pin() else null
        onDispose { pinnedHandle?.release() }
    }

    fullscreenState.value?.let { (view, exit) ->
        FullscreenPlayerOverlay(fullscreenView = view, onExit = exit)
    }
}

/**
 * Fullscreen trejler: View koji je dala biblioteka dodajemo direktno u decor view ISTOG prozora
 * aktivnosti, preko svega, u landscape-u i bez sistemskih traka.
 *
 * NAMERNO ne Dialog (prva verzija): Dialog je zaseban prozor, a video površina WebView-a je vezana
 * za prozor u kom je nastala - premeštanje u drugi prozor ju je uništavalo i plejer se gasio čim se
 * uđe u fullscreen. Isti pristup (isti prozor) koristi i zvanični primer biblioteke.
 */
@Composable
private fun FullscreenPlayerOverlay(fullscreenView: View, onExit: () -> Unit) {
    val activity = LocalContext.current.findActivity() ?: return

    // Sistemsko "nazad" izlazi iz fullscreen-a umesto da napusti ekran filma.
    BackHandler(onBack = onExit)

    DisposableEffect(fullscreenView, activity) {
        val decorView = activity.window.decorView as ViewGroup
        val previousOrientation = activity.requestedOrientation
        val insetsController = WindowCompat.getInsetsController(activity.window, decorView)

        (fullscreenView.parent as? ViewGroup)?.removeView(fullscreenView)
        fullscreenView.setBackgroundColor(android.graphics.Color.BLACK)
        decorView.addView(
            fullscreenView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            decorView.removeView(fullscreenView)
            activity.requestedOrientation = previousOrientation
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

fun Context.openYouTube(videoKey: String) {
    startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=$videoKey".toUri()))
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
