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
 * The "movie-nee" wordmark: "movie" in the text color, "nee" in the brand red
 * ([MaterialTheme.colorScheme.primary]), both bold italic (the closest match to the logo without
 * bundling a font). Shown on Home and Sign In.
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
 * Small subtitle under the wordmark explaining the name (movie + matinee = movienee), in the same
 * two-color style as [MovieneeWordmark] but smaller and muted. Always in English on purpose - a
 * translation would break the wordplay.
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
