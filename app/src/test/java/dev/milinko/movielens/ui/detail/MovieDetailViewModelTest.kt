package dev.milinko.movielens.ui.detail

import dev.milinko.movielens.MainDispatcherRule
import dev.milinko.movielens.data.location.LocationHelper
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val userRepository = mockk<UserRepository>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private lateinit var viewModel: MovieDetailViewModel

    @Before
    fun setUp() {
        every { sessionManager.getUserId() } returns "u1"
        viewModel = MovieDetailViewModel(
            mockk<MovieRepository>(relaxed = true),
            userRepository,
            sessionManager,
            mockk<LocationHelper>(relaxed = true)
        )
    }

    @Test
    fun `rating is shown immediately and kept when the server accepts it`() = runTest(mainRule.dispatcher) {
        coEvery { userRepository.addToHistory("u1", 550, rating = 8.0) } returns Result.success(mockk(relaxed = true))

        assertTrue(viewModel.rateMovie(550, 8))
        // Optimistic: before the network call has even run.
        assertEquals(8.0, viewModel.uiState.value.userRating, 0.0)
        assertTrue(viewModel.uiState.value.isWatched)

        advanceUntilIdle()
        assertEquals(8.0, viewModel.uiState.value.userRating, 0.0)
        assertTrue(viewModel.uiState.value.isWatched)
    }

    @Test
    fun `rating is rolled back when the server rejects it`() = runTest(mainRule.dispatcher) {
        coEvery { userRepository.addToHistory(any(), any(), any(), any(), any()) } returns Result.failure(IOException("offline"))

        viewModel.rateMovie(550, 8)
        advanceUntilIdle()

        assertEquals(0.0, viewModel.uiState.value.userRating, 0.0)
        assertFalse(viewModel.uiState.value.isWatched)
    }

    @Test
    fun `re-rating a watched movie updates instead of adding again`() = runTest(mainRule.dispatcher) {
        coEvery { userRepository.addToHistory(any(), any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))
        coEvery { userRepository.updateHistoryItem(any(), any(), any(), any()) } returns Result.success(mockk(relaxed = true))

        viewModel.rateMovie(550, 6)
        advanceUntilIdle()
        viewModel.rateMovie(550, 9)
        advanceUntilIdle()

        coVerify(exactly = 1) { userRepository.addToHistory(any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { userRepository.updateHistoryItem("u1", 550, 9.0, any()) }
    }

    @Test
    fun `watchlist toggle is rolled back on failure`() = runTest(mainRule.dispatcher) {
        coEvery { userRepository.addToWatchlist(any(), any()) } returns Result.failure(IOException("offline"))

        viewModel.toggleWatchlist(550)
        assertTrue(viewModel.uiState.value.isInWatchlist)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isInWatchlist)
    }

    @Test
    fun `signed out user is asked to sign in instead of calling the server`() = runTest(mainRule.dispatcher) {
        every { sessionManager.getUserId() } returns null

        assertFalse(viewModel.rateMovie(550, 8))
        assertFalse(viewModel.toggleWatchlist(550))
        advanceUntilIdle()

        coVerify(exactly = 0) { userRepository.addToHistory(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { userRepository.addToWatchlist(any(), any()) }
    }
}
