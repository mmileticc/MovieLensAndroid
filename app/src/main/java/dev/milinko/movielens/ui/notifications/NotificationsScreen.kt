package dev.milinko.movielens.ui.notifications

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.milinko.movielens.R
import dev.milinko.movielens.data.push.PushNotifier
import dev.milinko.movielens.data.push.PushType
import dev.milinko.movielens.data.remote.user.NotificationItem
import dev.milinko.movielens.ui.components.ErrorCard
import dev.milinko.movielens.ui.components.SignInRequiredState

/** Notification history; tapping an item opens the movie, the friend's profile or Friends. */
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load(markRead = true) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(stringResource(R.string.notifications_title), style = MaterialTheme.typography.titleMedium)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.requiresSignIn -> SignInRequiredState(
                    title = stringResource(R.string.notifications_sign_in_title),
                    message = stringResource(R.string.notifications_sign_in_message),
                    buttonText = stringResource(R.string.profile_sign_in),
                    onSignInClick = onNavigateToSignIn
                )
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null && uiState.items.isEmpty() -> ErrorCard(
                    message = uiState.error.orEmpty(),
                    onRetry = { viewModel.load(markRead = true) },
                    modifier = Modifier.padding(16.dp)
                )
                uiState.items.isEmpty() -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.notifications_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(uiState.items, key = { it.id }) { item ->
                        NotificationRow(item = item, onClick = onNavigateToRoute)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private fun PushType.icon(): ImageVector = when (this) {
    PushType.FRIEND_REQUEST, PushType.FRIEND_ACCEPTED -> Icons.Default.PersonAdd
    PushType.COMMENT_LIKED -> Icons.Default.Favorite
    PushType.MOVIE_SHARED -> Icons.Default.Send
    PushType.WATCHLIST_IN_CINEMA -> Icons.Default.Theaters
}

@Composable
private fun NotificationRow(item: NotificationItem, onClick: (String) -> Unit) {
    val type = PushType.fromWire(item.type)
    // Text is built the same way as for the push (in the app's language); an unknown/older type
    // falls back to the text from the server.
    val content = type?.let { PushNotifier.contentFor(it, item.data.orEmpty()) }
    val title = content?.title ?: item.content
    val body = content?.body
    val route = content?.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (route != null) Modifier.clickable { onClick(route) } else Modifier)
            .background(if (item.isRead) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = type?.icon() ?: Icons.Default.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold
            )
            if (body != null) {
                Text(text = body, style = MaterialTheme.typography.bodyMedium)
            }
            item.createdAt?.let {
                Text(
                    text = DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (!item.isRead) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
