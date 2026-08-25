package dev.milinko.movielens.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Rebrending (25. avgust 2026) - deljeni "movie‑nee" wordmark umesto običnog stringResource(app_name)
 * teksta jednobojno. Vizuelni identitet iz korisnikovog loga: "movie" u boji teksta (bela u tamnoj
 * temi), "nee" u brend crvenoj (isti [MaterialTheme.colorScheme.primary] token kao ostatak app-a -
 * nema potrebe za novom bojom), oba dela bold+italic (nemamo bundled kurzivni serif font iz loga,
 * pa se bold+italic sistemski font koristi kao najbliža aproksimacija bez rizika dodavanja novog
 * font asset-a). Koristi se na Home i Sign In ekranu (dva najistaknutija mesta za brend) umesto
 * ranijeg jednobojnog stringResource(R.string.app_name) teksta.
 */
@Composable
fun MovieneeWordmark(
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    baseColor: Color = MaterialTheme.colorScheme.onBackground
) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = baseColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
            append("movie")
        }
        withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
            append("nee")
        }
    }
    Text(text = text, style = style, modifier = modifier)
}

/**
 * Dorada (25. avgust 2026, korisnička primedba "nisi nigde dodao ovaj deo movie matine koji je
 * objašnjenje malo naziva") - sitan podnaslov ispod glavnog wordmark-a koji objašnjava odakle ime
 * dolazi (movie + matinee = movienee), isti obrazac dvobojnosti kao [MovieneeWordmark] ali sitnije
 * i priglušenije (onSurfaceVariant/accent umesto onBackground/accent) - u logu je ovo mala, tiha
 * napomena ispod velikog imena, ne novi naslov. NAMERNO na engleskom (isto kao original) - prevod
 * bi razbio igru reči koju objašnjava.
 */
@Composable
fun MovieneeNameOrigin(
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    baseColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = baseColor, fontStyle = FontStyle.Italic)) {
            append("movie ")
        }
        withStyle(SpanStyle(color = accentColor, fontStyle = FontStyle.Italic)) {
            append("matinee")
        }
    }
    Text(text = text, style = style, modifier = modifier)
}
