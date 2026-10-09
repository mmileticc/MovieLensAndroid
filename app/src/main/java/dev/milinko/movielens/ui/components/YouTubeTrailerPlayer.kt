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
 * The movie's trailer(s) played in the app (YouTube IFrame player in a WebView, via the
 * android-youtube-player library). With several videos, a row of chips above the player picks one.
 *
 * - The video is only cued (does not autoplay), so no data is used until the user presses play.
 * - Fullscreen: the library hands over a ready fullscreen View, which is added over the whole screen
 *   in the SAME window (see FullscreenPlayerOverlay) and the activity switches to landscape
 *   (MainActivity handles orientation configChanges, so the activity and the player survive rotation).
 * - Some studios forbid embedding (or the player cannot play the video for another reason); then
 *   "Open on YouTube" is shown instead of the player.
 */
@Composable
fun YouTubeTrailerPlayer(
    videos: List<MovieVideo>,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) return

    var selectedIndex by remember(videos) { mutableIntStateOf(0) }
    val selected = videos[selectedIndex.coerceIn(videos.indices)]
    // Failures are remembered per video key - another trailer of the same movie may still play.
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

    // Plain MutableState holders (not `by` delegates): the View's listeners write them from anonymous classes.
    val playerState = remember { mutableStateOf<YouTubePlayer?>(null) }
    val fullscreenState = remember { mutableStateOf<Pair<View, () -> Unit>?>(null) }
    // Listeners are registered once (when the View is created) but must see the current key/callback.
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

    // Switching the selected trailer after the first load (the first key is cued in onReady).
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

    // While in fullscreen, pin this LazyColumn item (Movie Detail). Otherwise, after rotating to
    // landscape (height ~400dp) the trailer scrolls out of view, LazyColumn disposes it, onDispose
    // releases the player and fullscreen ends. Must come BEFORE FullscreenPlayerOverlay below:
    // effects run in order, so the item is pinned before the overlay requests the orientation change.
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
 * Fullscreen trailer: the View provided by the library is added directly to the decor view of the
 * SAME activity window, above everything, in landscape and without system bars.
 *
 * Deliberately not a Dialog: a Dialog is a separate window, and the WebView's video surface is tied
 * to the window it was created in - moving it destroyed the surface and playback stopped on entering
 * fullscreen. The library's official sample uses the same approach.
 */
@Composable
private fun FullscreenPlayerOverlay(fullscreenView: View, onExit: () -> Unit) {
    val activity = LocalContext.current.findActivity() ?: return

    // System "back" leaves fullscreen instead of leaving the movie screen.
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

/** Opens the video in the YouTube app (or the browser). */
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
