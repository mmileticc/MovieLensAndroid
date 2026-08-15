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

/**
 * Varijante dugmeta iz MOVIELENS_DESIGN_PLAN.md 1.4 - Primary (puna crvena, glavna akcija),
 * Secondary (obrisano dugme sa outline, sporedna akcija). Faza 6 redizajna.
 *
 * `leadingIcon` dodat u Fazi 5 (Movie Detail) - Watchlist/Odgledano dugmad menjaju i ikonicu i
 * tekst zavisno od stanja (npr. FavoriteBorder->Favorite), pa je opcioni slot neophodan da bi
 * AppButton uopšte mogao da ih zameni umesto ad-hoc Button/OutlinedButton poziva.
 */
enum class AppButtonVariant { Primary, Secondary }

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
