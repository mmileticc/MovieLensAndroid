package dev.milinko.movielens.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.milinko.movielens.R
import dev.milinko.movielens.data.remote.user.FriendInfo
import dev.milinko.movielens.ui.components.RemoteAvatarImage

private const val NOTE_MAX_LENGTH = 200

/** Izbor prijatelja (i opciona poruka) kome se šalje film - stiže mu kao push obaveštenje. */
@Composable
internal fun ShareWithFriendDialog(
    movieTitle: String,
    friends: List<FriendInfo>,
    isLoadingFriends: Boolean,
    isSending: Boolean,
    onSend: (friendId: String, note: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_friend_dialog_title, movieTitle)) },
        text = {
            Column {
                when {
                    isLoadingFriends && friends.isEmpty() -> Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                    friends.isEmpty() -> Text(
                        stringResource(R.string.share_friend_no_friends),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(friends, key = { it.friendId }) { friend ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedId == friend.friendId,
                                        role = Role.RadioButton,
                                        onClick = { selectedId = friend.friendId }
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selectedId == friend.friendId, onClick = null)
                                Spacer(modifier = Modifier.size(8.dp))
                                RemoteAvatarImage(
                                    url = friend.avatar,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Text(friend.username, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                if (friends.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(12.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { if (it.length <= NOTE_MAX_LENGTH) note = it },
                        label = { Text(stringResource(R.string.share_friend_note_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedId?.let { onSend(it, note.trim().ifBlank { null }) } },
                enabled = selectedId != null && !isSending
            ) { Text(stringResource(R.string.share_friend_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_remove_cancel)) }
        }
    )
}
