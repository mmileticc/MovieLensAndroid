package dev.milinko.movielens.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Button variants: Primary (filled red, main action) and Secondary (outlined, secondary action). */
enum class AppButtonVariant { Primary, Secondary }

/**
 * The app's standard button. The optional `leadingIcon` lets stateful buttons (e.g. Watchlist /
 * Watched) change both the icon and the text.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null
) {
    val content: @Composable () -> Unit = {
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }

    when (variant) {
        AppButtonVariant.Primary -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) { content() }

        AppButtonVariant.Secondary -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onBackground
            )
        ) { content() }
    }
}
