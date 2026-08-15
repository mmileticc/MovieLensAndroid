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
 * Mala zlatna značka koja obeležava premium funkcionalnost (npr. pretraga slikom u Search-u -
 * troši Gemini zahteve, videti korisnikov feedback u MOVIELENS_DESIGN_PLAN.md 2.3). Za sada čisto
 * vizuelno - nema logiku ograničenja korišćenja (korisnik: "za ovaj premium cemo prvo da uradimo
 * vizuelno a kasnije cemo da menjamo backend za usage"). Namerno malena (14dp) da se lepo uklapa
 * kao overlay bedž preko ikonice, ne kao samostalan element.
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
