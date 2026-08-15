package dev.milinko.movielens.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Mali "pilula" bedž za žanr (npr. na Movie Detail ekranu). Koristi `MaterialTheme.shapes.small`
 * (8dp, videti MOVIELENS_DESIGN_PLAN.md 1.3 - "small = 8dp - čip, dugme, mali bedž"). Faza 6
 * redizajna.
 */
@Composable
fun GenreChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                MaterialTheme.shapes.small
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
