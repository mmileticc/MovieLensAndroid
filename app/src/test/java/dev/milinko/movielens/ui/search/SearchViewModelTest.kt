package dev.milinko.movielens.ui.search

import dev.milinko.movielens.MainDispatcherRule
import dev.milinko.movielens.data.pref.RecentSearchesStore
import dev.milinko.movielens.data.pref.SessionManager
import dev.milinko.movielens.data.remote.movie.MovieSearchResult
import dev.milinko.movielens.data.repository.MovieRepository
import dev.milinko.movielens.data.repository.VisionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val movieRepository = mockk<MovieRepository>()
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val recentSearchesStore = mockk<RecentSearchesStore>(relaxed = true)
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        every { sessionManager.getUserId() } returns null
        every { recentSearchesStore.getQueries(any()) } returns emptyList()
        viewModel = SearchViewModel(movieRepository, mockk<VisionRepository>(relaxed = true), recentSearchesStore, sessionManager)
    }

    private fun movie(id: Int, title: String) = MovieSearchResult(tmdbId = id, title = title)

    @Test
    fun `typing quickly sends only the last query after the debounce`() = runTest(mainRule.dispatcher) {
        coEvery { movieRepository.searchMovies(any()) } returns Result.success(listOf(movie(1, "Batman")))

        viewModel.setSearchQuery("ba")
        advanceTimeBy(100)
        viewModel.setSearchQuery("bat")
        advanceTimeBy(100)
        viewModel.setSearchQuery("batm")
        advanceUntilIdle()

        coVerify(exactly = 1) { movieRepository.searchMovies(any()) }
        coVerify { movieRepository.searchMovies("batm") }
        assertEquals(listOf(movie(1, "Batman")), viewModel.uiState.value.searchResults)
    }

    @Test
    fun `query shorter than two characters does not hit the network`() = runTest(mainRule.dispatcher) {
        viewModel.setSearchQuery("b")
        advanceUntilIdle()

        coVerify(exactly = 0) { movieRepository.searchMovies(any()) }
        assertEquals(emptyList<MovieSearchResult>(), viewModel.uiState.value.searchResults)
    }

    @Test
    fun `request cancelled by a newer query never shows an error`() = runTest(mainRule.dispatcher) {
        coEvery { movieRepository.searchMovies("slow") } coAnswers {
            delay(5_000)
            Result.success(listOf(movie(1, "Stale")))
        }
        coEvery { movieRepository.searchMovies("fast") } returns Result.success(listOf(movie(2, "Fresh")))

        viewModel.setSearchQuery("slow")
        advanceTimeBy(1_000) // debounce passed, the slow request is in flight
        viewModel.setSearchQuery("fast")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.error)
        assertFalse(state.isSearching)
        assertEquals(listOf(movie(2, "Fresh")), state.searchResults)
    }

    @Test
    fun `failed search shows a friendly error`() = runTest(mainRule.dispatcher) {
        coEvery { movieRepository.searchMovies(any()) } returns Result.failure(IOException("Unable to resolve host"))

        viewModel.setSearchQuery("batman")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertEquals("res:${dev.milinko.movielens.R.string.error_network}", state.error)
    }
}
