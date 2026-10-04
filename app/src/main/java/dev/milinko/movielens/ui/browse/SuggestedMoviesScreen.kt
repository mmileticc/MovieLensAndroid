package dev.milinko.movielens.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import dev.milinko.movielens.R

/** "Predloženo za tebe" - Vidi sve (otvara se sa Home trake). */
@Composable
fun SuggestedMoviesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (Int) -> Unit,
    viewModel: SuggestedMoviesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            BrowseTopBar(
                title = stringResource(R.string.home_section_personalized),
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
                onToggleGenre = viewModel::onToggleGenre
            )
            BrowseStateContent(
                movies = uiState.movies,
                viewMode = uiState.viewMode,
                isLoading = uiState.isLoading,
                isRefreshing = uiState.isRefreshing,
                error = uiState.error,
                hasActiveFilters = uiState.hasActiveFilters,
                emptyTitle = stringResource(R.string.browse_suggested_empty),
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onMovieClick = onNavigateToDetail,
                isLoadingMore = uiState.isLoadingMore,
                onLoadMore = viewModel::loadMore
            )
        }
    }
}
