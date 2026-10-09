package dev.milinko.movielens.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milinko.movielens.R

/**
 * Generic expandable container: a `header` row plus `expandedContent` shown only when
 * `expanded == true` (used for the image-search results).
 *
 * With `headerTogglesExpansion = true` (default) the whole row is one tap target for expanding.
 * With `false`, only the arrow is an IconButton, so `header()` can have its own `.clickable {}`
 * (e.g. opening Movie Detail) without a conflict.
 */
@Composable
fun ExpandableMatchCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expandedContent: (@Composable () -> Unit)? = null,
    headerTogglesExpansion: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (headerTogglesExpansion && expandedContent != null) {
                        Modifier.clickable(
                            onClickLabel = stringResource(
                                if (expanded) R.string.expandable_card_collapse else R.string.expandable_card_expand
                            ),
                            onClick = onToggleExpanded
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) { header() }
            if (expandedContent != null) {
                if (headerTogglesExpansion) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(
                                if (expanded) R.string.expandable_card_collapse else R.string.expandable_card_expand
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (expanded && expandedContent != null) {
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                expandedContent()
            }
        }
    }
}
