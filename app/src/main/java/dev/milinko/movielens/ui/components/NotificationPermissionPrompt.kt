package dev.milinko.movielens.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import dev.milinko.movielens.R
import dev.milinko.movielens.data.push.NotificationPreferences

/**
 * Android 13+: jednom, posle prijave, objasni čemu služe obaveštenja i tek onda zatraži sistemsku
 * dozvolu - sistemski dijalog bez objašnjenja korisnici često odbiju, a drugi put se ne prikazuje.
 */
@Composable
fun NotificationPermissionPrompt(
    trigger: Any?,
    isSignedIn: () -> Boolean,
    preferences: NotificationPreferences
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(trigger) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && isSignedIn() && !preferences.permissionPromptShown) {
            showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                preferences.permissionPromptShown = true
            },
            title = { Text(stringResource(R.string.notif_permission_title)) },
            text = { Text(stringResource(R.string.notif_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    preferences.permissionPromptShown = true
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.notif_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    preferences.permissionPromptShown = true
                }) { Text(stringResource(R.string.notif_permission_later)) }
            }
        )
    }
}
