package dev.milinko.movielens.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BottomNavTab {
    HOME, SEARCH, LIBRARY, PROFILE
}

@Composable
fun AppBottomNavigationBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBottomNavItem("Home", Icons.Default.Home, selectedTab == BottomNavTab.HOME) {
                onTabSelected(BottomNavTab.HOME)
            }
            AppBottomNavItem("Pretraga", Icons.Default.Search, selectedTab == BottomNavTab.SEARCH) {
                onTabSelected(BottomNavTab.SEARCH)
            }
            AppBottomNavItem("Biblioteka",
                Icons.AutoMirrored.Filled.LibraryBooks, selectedTab == BottomNavTab.LIBRARY) {
                onTabSelected(BottomNavTab.LIBRARY)
            }
            AppBottomNavItem("Profil", Icons.Default.Settings, selectedTab == BottomNavTab.PROFILE) {
                onTabSelected(BottomNavTab.PROFILE)
            }
        }
    }
}

@Composable
private fun AppBottomNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

