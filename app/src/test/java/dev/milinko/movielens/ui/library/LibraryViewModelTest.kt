package dev.milinko.movielens.ui.library

import dev.milinko.movielens.MainDispatcherRule
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.Genre
import dev.milinko.movielens.data.remote.movie.HistoryItem
import dev.milinko.movielens.data.remote.movie.WatchlistItem
import dev.milinko.movielens.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val action = Genre(28, "Action")
    private val comedy = Genre(35, "Comedy")

    private val watchlist = listOf(
        WatchlistItem(tmdbId = 1, title = "Batman", addedAt = 300, genres = listOf(action), releaseYear = 2022, voteAverage = 7.8),
        WatchlistItem(tmdbId = 2, title = "Airplane", addedAt = 100, genres = listOf(comedy), releaseYear = 1980, voteAverage = 7.7),
        WatchlistItem(tmdbId = 3, title = "Die Hard", addedAt = 200, genres = listOf(action), releaseYear = 1988, voteAverage = 8.2)
    )
    private val history = listOf(
        HistoryItem(tmdbId = 10, title = "Heat", userRating = 9.0, watchedAt = 10, genres = listOf(action)),
        HistoryItem(tmdbId = 11, title = "Hot Shots", userRating = 5.0, watchedAt = 20, genres = listOf(comedy))
    )

    private val userRepository = mockk<UserRepository>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        every { sessionManager.getUserId() } returns "u1"
        every { userRepository.observeWatchlist("u1") } returns flowOf(watchlist)
        every { userRepository.observeHistory("u1") } returns flowOf(history)
        coEvery { userRepository.getWatchlist("u1") } returns Result.success(watchlist)
        coEvery { userRepository.getHistory("u1") } returns Result.success(history)
        viewModel = LibraryViewModel(userRepository, sessionManager)
    }

    private fun watchlistIds() = viewModel.uiState.value.watchlist.map { it.tmdbId }

    @Test
    fun `signed out user sees sign-in prompt`() = runTest(mainRule.dispatcher) {
        every { sessionManager.getUserId() } returns null
        viewModel.loadLibrary()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.requiresSignIn)
    }

    @Test
    fun `watchlist is newest first by default and can be sorted`() = runTest(mainRule.dispatcher) {
        viewModel.loadLibrary()
        advanceUntilIdle()
        assertEquals(listOf(1, 3, 2), watchlistIds())

        viewModel.onWatchlistSortChange(WatchlistSort.TITLE)
        advanceUntilIdle()
        assertEquals(listOf(2, 1, 3), watchlistIds())

        viewModel.onWatchlistSortChange(WatchlistSort.TMDB_RATING)
        advanceUntilIdle()
        assertEquals(listOf(3, 1, 2), watchlistIds())
    }

    @Test
    fun `text and genre filters combine and keep the unfiltered total`() = runTest(mainRule.dispatcher) {
        viewModel.loadLibrary()
        viewModel.onToggleGenre(action.id)
        viewModel.onQueryChange("die")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(3), watchlistIds())
        assertEquals(3, state.watchlistTotal)
        assertTrue(state.hasActiveFilters)
        assertEquals(listOf(action, comedy), state.availableGenres)
    }

    @Test
    fun `minimum own rating filters the watched tab`() = runTest(mainRule.dispatcher) {
        viewModel.loadLibrary()
        viewModel.onMinMyRatingChange(8)
        advanceUntilIdle()

        assertEquals(listOf(10), viewModel.uiState.value.history.map { it.tmdbId })
    }

    @Test
    fun `removal is hidden at once, undo brings it back without a server call`() = runTest(mainRule.dispatcher) {
        viewModel.loadLibrary()
        advanceUntilIdle()

        viewModel.requestRemove(watchlist[0])
        advanceUntilIdle()
        assertEquals(listOf(3, 2), watchlistIds())

        viewModel.undoRemove(1)
        advanceUntilIdle()
        assertEquals(listOf(1, 3, 2), watchlistIds())
        coVerify(exactly = 0) { userRepository.removeFromWatchlist(any(), any()) }
    }

    @Test
    fun `committed removal calls the server once`() = runTest(mainRule.dispatcher) {
        coEvery { userRepository.removeFromWatchlist("u1", 1) } returns Result.success(Unit)
        viewModel.loadLibrary()
        advanceUntilIdle()

        viewModel.requestRemove(watchlist[0])
        viewModel.commitAllPendingRemovals()
        viewModel.commitRemove(1) // a second call (e.g. the snackbar expired) must not send a duplicate request
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.removeFromWatchlist("u1", 1) }
    }
}
