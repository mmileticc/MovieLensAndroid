package dev.milinko.movielens.data.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.milinko.movielens.MainActivity
import dev.milinko.movielens.R
import dev.milinko.movielens.navigation.Screen
import dev.milinko.movielens.util.AppLocale

/**
 * Prikaz push obaveštenja. Backend šalje samo podatke (tip, ime prijatelja, film...), a tekst se
 * sastavlja ovde iz stringova aplikacije - uvek na jeziku aplikacije.
 */
object PushNotifier {

    /** Ruta na koju MainActivity navigira kad korisnik dodirne obaveštenje. */
    const val EXTRA_OPEN_ROUTE = "dev.milinko.movielens.OPEN_ROUTE"

    private const val CHANNEL_FRIENDS = "friends"
    private const val CHANNEL_COMMENTS = "comments"
    private const val CHANNEL_MOVIES = "movies"

    /** Kanali po vrsti (korisnik ih može zasebno isključiti i u podešavanjima telefona). */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_FRIENDS, AppLocale.string(R.string.notif_channel_friends), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_COMMENTS, AppLocale.string(R.string.notif_channel_comments), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_MOVIES, AppLocale.string(R.string.notif_channel_movies), NotificationManager.IMPORTANCE_DEFAULT)
            )
        )
    }

    fun show(context: Context, data: Map<String, String>, preferences: NotificationPreferences) {
        val type = PushType.fromWire(data["type"]) ?: return
        if (!preferences.isEnabled(type)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val content = contentFor(type, data) ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ROUTE, content.route)
        }
        val notificationId = (type.name + content.route).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, content.channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, notification) }
    }

    private data class Content(val channel: String, val title: String, val body: String, val route: String)

    private fun contentFor(type: PushType, data: Map<String, String>): Content? {
        val actor = data["actorName"]?.takeIf { it.isNotBlank() } ?: AppLocale.string(R.string.profile_default_username)
        val actorId = data["actorId"]
        val tmdbId = data["tmdbId"]?.toIntOrNull()
        val movieTitle = data["movieTitle"]?.takeIf { it.isNotBlank() }
        return when (type) {
            PushType.FRIEND_REQUEST -> Content(
                CHANNEL_FRIENDS,
                AppLocale.string(R.string.notif_friend_request_title),
                AppLocale.string(R.string.notif_friend_request_body, actor),
                Screen.Friends.route
            )
            PushType.FRIEND_ACCEPTED -> Content(
                CHANNEL_FRIENDS,
                AppLocale.string(R.string.notif_friend_accepted_title),
                AppLocale.string(R.string.notif_friend_accepted_body, actor),
                actorId?.let { Screen.FriendProfile.createRoute(it) } ?: Screen.Friends.route
            )
            PushType.COMMENT_LIKED -> Content(
                CHANNEL_COMMENTS,
                AppLocale.string(R.string.notif_comment_liked_title),
                if (movieTitle != null) AppLocale.string(R.string.notif_comment_liked_body, actor, movieTitle)
                else AppLocale.string(R.string.notif_comment_liked_body_no_movie, actor),
                tmdbId?.let { Screen.MovieDetail.createRoute(it) } ?: return null
            )
            PushType.MOVIE_SHARED -> Content(
                CHANNEL_FRIENDS,
                AppLocale.string(R.string.notif_movie_shared_title),
                AppLocale.string(R.string.notif_movie_shared_body, actor, movieTitle ?: ""),
                tmdbId?.let { Screen.MovieDetail.createRoute(it) } ?: return null
            )
            PushType.WATCHLIST_IN_CINEMA -> Content(
                CHANNEL_MOVIES,
                AppLocale.string(R.string.notif_watchlist_cinema_title),
                AppLocale.string(R.string.notif_watchlist_cinema_body, movieTitle ?: ""),
                tmdbId?.let { Screen.MovieDetail.createRoute(it) } ?: return null
            )
        }
    }
}
