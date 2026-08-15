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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Generički sklopivi/rasklopivi kontejner: red sa `header` sadržajem (uvek vidljiv, klikom se
 * širi/skuplja) + `expandedContent` koji se prikazuje samo kada je `expanded == true`. Napravljena
 * za rezultate pretrage slikom (Faza 8/Search: hero pogodak + sklopivi sekundarni pogoci, videti
 * MOVIELENS_DESIGN_PLAN.md 2.3 - korisnikov feedback: "trenutno uzme prikaze... izlista njih sve
 * sto je dosta nepregledno, to bi mozda trebalo da se klikom prikaze ili sakrije"), ali je NAMERNO
 * generička (ne zna ništa o CandidateResolution/TmdbMatch/ScrapedMovie) da se po potrebi može
 * koristiti i drugde - konkretno vision-search ožičavanje ide u Fazi 8 kad se pravi Search ekran.
 */
@Composable
fun ExpandableMatchCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expandedContent: (@Composable () -> Unit)? = null
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
                .clickable(onClick = onToggleExpanded)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) { header() }
            if (expandedContent != null) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (expanded && expandedContent != null) {
            Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                expandedContent()
            }
        }
    }
}
