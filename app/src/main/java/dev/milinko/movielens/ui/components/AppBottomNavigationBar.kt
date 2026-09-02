package dev.milinko.movielens.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.milinko.movielens.R

enum class BottomNavTab {
    HOME, SEARCH, LIBRARY, PROFILE
}

/**
 * Faza 3 redizajna (videti MOVIELENS_DESIGN_PLAN.md 3): puna širina (korisnikova odluka - manje
 * rizika, predvidljivije ponašanje uz insets/tastaturu na Search ekranu), pozadina
 * `colorScheme.surface` (= MlSurfaceElevated1 iz teme), aktivni tab dobija `accentPrimary`
 * ikonicu + suptilnu "pill" pozadinu iza ikonice umesto samo promene boje. Popravljen postojeći
 * bag: ikonica za "Profil" je bila Settings (zupčanik) umesto Person - nije bila dizajn odluka,
 * samo pogrešna ikonica. Tekst natpisa prebačen u strings.xml po novoj konvenciji lokalizacije.
 */
@Composable
fun AppBottomNavigationBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    // Bugfix (E1.6, 2. septembar 2026, feedback testera - "nav traka ide ispod sistemske nav trake
    // na nekim verzijama") - RANIJE je Surface imao fiksnu `.height(64.dp)` bez ikakvog insets
    // podešavanja, pa se na gesture-navigation (ili nekim 3-dugme) uređajima sadržaj trake
    // preklapao sa sistemskom navigacijom na dnu ekrana. `.navigationBarsPadding()` na Surface-u
    // dodaje dodatni razmak VELIČINE sistemskog dna ISPOD sadržaja (koji i dalje ima svojih 64dp,
    // sad na unutrašnjem Row-u), umesto da se ceo Surface samo suzi/prekrije.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBottomNavItem(
                label = stringResource(R.string.nav_home),
                icon = Icons.Default.Home,
                selected = selectedTab == BottomNavTab.HOME
            ) { onTabSelected(BottomNavTab.HOME) }

            AppBottomNavItem(
                label = stringResource(R.string.nav_search),
                icon = Icons.Default.Search,
                selected = selectedTab == BottomNavTab.SEARCH
            ) { onTabSelected(BottomNavTab.SEARCH) }

            AppBottomNavItem(
                label = stringResource(R.string.nav_library),
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                selected = selectedTab == BottomNavTab.LIBRARY
            ) { onTabSelected(BottomNavTab.LIBRARY) }

            AppBottomNavItem(
                label = stringResource(R.string.nav_profile),
                icon = Icons.Default.Person,
                selected = selectedTab == BottomNavTab.PROFILE
            ) { onTabSelected(BottomNavTab.PROFILE) }
        }
    }
}

@Composable
private fun AppBottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
