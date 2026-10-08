package dev.milinko.movielens.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.milinko.movielens.R
import dev.milinko.movielens.data.push.NotificationPreferences
import dev.milinko.movielens.data.push.PushType
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferences: NotificationPreferences
) : ViewModel() {
    val enabled = preferences.enabled
    fun setEnabled(type: PushType, enabled: Boolean) = preferences.setEnabled(type, enabled)
}

@StringRes
private fun PushType.labelRes(): Int = when (this) {
    PushType.FRIEND_REQUEST -> R.string.settings_notif_friend_request
    PushType.FRIEND_ACCEPTED -> R.string.settings_notif_friend_accepted
    PushType.COMMENT_LIKED -> R.string.settings_notif_comment_liked
    PushType.MOVIE_SHARED -> R.string.settings_notif_movie_shared
    PushType.WATCHLIST_IN_CINEMA -> R.string.settings_notif_watchlist_cinema
}

/** Prekidači po vrsti obaveštenja (čuvaju se lokalno, videti NotificationPreferences). */
@Composable
internal fun NotificationSettingsSection(viewModel: NotificationSettingsViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()

    Text(
        text = stringResource(R.string.settings_notifications_header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 12.dp)
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            PushType.entries.forEach { type ->
                val checked = enabled[type] ?: true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = { viewModel.setEnabled(type, it) }
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(type.labelRes()), modifier = Modifier.weight(1f))
                    // Klik obrađuje ceo red (veća dodirna površina, TalkBack čita red kao jedan prekidač).
                    Switch(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
