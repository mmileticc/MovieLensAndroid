package dev.milinko.movielens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milinko.movielens.R

/**
 * Small gold badge marking a premium feature (e.g. image search, which uses Gemini requests).
 * Visual only for now, with no usage limits. Small (14dp) on purpose, to sit as an overlay on an icon.
 */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = stringResource(R.string.premium_badge_content_description),
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(9.dp)
        )
    }
}
