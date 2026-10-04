package dev.milinko.movielens.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R

/** "U bioskopima" - Vidi sve (otvara se sa Home trake "U bioskopima sada"). */
@Composable
fun NowInCinemasScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    viewModel: NowInCinemasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            BrowseTopBar(
                title = stringResource(R.string.home_section_now_in_cinemas),
                viewMode = uiState.viewMode,
                onNavigateBack = onNavigateBack,
                onToggleViewMode = viewModel::toggleViewMode
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            BrowseFilterBar(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                genres = uiState.availableGenres,
                selectedGenreIds = uiState.selectedGenreIds,
                onToggleGenre = viewModel::onToggleGenre,
                extraFilters = {
                    CinemaChips(
                        cinemas = uiState.availableCinemas,
                        selected = uiState.selectedCinemas,
                        onToggle = viewModel::onToggleCinema
                    )
                    StatusAndSortRow(
                        status = uiState.status,
                        onStatusChange = viewModel::onStatusChange,
                        sort = uiState.sort,
                        onSortChange = viewModel::onSortChange
                    )
                }
            )
            BrowseStateContent(
                movies = uiState.movies,
                viewMode = uiState.viewMode,
                isLoading = uiState.isLoading,
                isRefreshing = uiState.isRefreshing,
                error = uiState.error,
                hasActiveFilters = uiState.hasActiveFilters,
                emptyTitle = stringResource(R.string.browse_cinemas_empty),
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onMovieClick = onNavigateToDetail
            )
        }
    }
}

@Composable
private fun CinemaChips(cinemas: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    if (cinemas.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        items(cinemas, key = { it }) { cinema ->
            FilterChip(
                selected = cinema in selected,
                onClick = { onToggle(cinema) },
                label = { Text(cinema) }
            )
        }
    }
}

@Composable
private fun StatusAndSortRow(
    status: CinemaStatusFilter,
    onStatusChange: (CinemaStatusFilter) -> Unit,
    sort: CinemaSort,
    onSortChange: (CinemaSort) -> Unit
) {
    val statusOptions = listOf(
        CinemaStatusFilter.ALL to stringResource(R.string.browse_status_all),
        CinemaStatusFilter.NOW to stringResource(R.string.browse_status_now),
        CinemaStatusFilter.UPCOMING to stringResource(R.string.browse_status_upcoming)
    )
    val sortOptions = listOf(
        CinemaSort.RATING to stringResource(R.string.browse_sort_rating),
        CinemaSort.TITLE to stringResource(R.string.browse_sort_title),
        CinemaSort.CINEMA_COUNT to stringResource(R.string.browse_sort_cinema_count)
    )
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            statusOptions.forEachIndexed { index, (option, label) ->
                SegmentedButton(
                    selected = status == option,
                    onClick = { onStatusChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = statusOptions.size),
                    icon = {}
                ) {
                    Text(label, maxLines = 1)
                }
            }
        }
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.browse_sort_content_description))
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                sortOptions.forEach { (option, label) ->
                    DropdownMenuItem(
                        text = { Text(if (option == sort) "✓ $label" else label) },
                        onClick = {
                            onSortChange(option)
                            sortMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}
