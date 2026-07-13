package com.tcohen.moviesapp.presentation.moviedetail.morelikethis

import app.cash.turbine.test
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.domain.repository.MovieRepository
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [MoreLikeThisViewModel].
 *
 * Verifies every branch of the [MoreLikeThisState] sealed type is reachable and
 * that the [MoreLikeThisEffect.NavigateToDetail] effect is produced for taps.
 *
 * Offline handling is intentionally not asserted here — that's the responsibility
 * of [com.tcohen.moviesapp.data.remote.api.SafeApiCaller], exercised by
 * [com.tcohen.moviesapp.data.remote.api.SafeApiCallTest]. The view-model here
 * receives whatever [NetworkResult] the repository returns; the test suite confirms
 * it can render every shape the wrapper produces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoreLikeThisViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MovieRepository = mockk()

    private fun createViewModel(): MoreLikeThisViewModel = MoreLikeThisViewModel(repository)

    // ── Success path ───────────────────────────────────────────────────────────

    @Test
    fun `Load with non-empty result transitions through Loading to Success`() = runTest {
        val movies = listOf(fakeMovie(id = 1), fakeMovie(id = 2))
        coEvery { repository.getSimilarMovies(42) } returns NetworkResult.Success(movies)

        val vm = createViewModel()
        vm.state.test {
            // Initial state — Idle.
            assertEquals(MoreLikeThisState.Idle, awaitItem())

            vm.processIntent(MoreLikeThisIntent.Load(movieId = 42))
            // Loading → Success.
            assertEquals(MoreLikeThisState.Loading, awaitItem())
            val success = awaitItem()
            assertTrue(success is MoreLikeThisState.Success)
            assertEquals(movies, (success as MoreLikeThisState.Success).movies)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Empty result ──────────────────────────────────────────────────────────

    @Test
    fun `Load with empty result lands in Empty (Success skipped)`() = runTest {
        coEvery { repository.getSimilarMovies(42) } returns NetworkResult.Success(emptyList())

        val vm = createViewModel()
        vm.state.test {
            assertEquals(MoreLikeThisState.Idle, awaitItem())
            vm.processIntent(MoreLikeThisIntent.Load(42))
            assertEquals(MoreLikeThisState.Loading, awaitItem())
            assertEquals(MoreLikeThisState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Error mapping ──────────────────────────────────────────────────────────

    @Test
    fun `Load with Error from repository lands in Error state with the message`() = runTest {
        val serverMessage = "Service temporarily unavailable."
        coEvery { repository.getSimilarMovies(42) } returns NetworkResult.Error(serverMessage, 503)

        val vm = createViewModel()
        vm.state.test {
            assertEquals(MoreLikeThisState.Idle, awaitItem())
            vm.processIntent(MoreLikeThisIntent.Load(42))
            assertEquals(MoreLikeThisState.Loading, awaitItem())
            val err = awaitItem()
            assertTrue(err is MoreLikeThisState.Error)
            assertEquals(serverMessage, (err as MoreLikeThisState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Load with NO_CONNECTION-shaped Error surfaces the no-connection message`() = runTest {
        coEvery { repository.getSimilarMovies(42) } returns NetworkResult.Error(
            message = com.tcohen.moviesapp.util.ApiError.NO_CONNECTION.message
        )

        val vm = createViewModel()
        vm.state.test {
            assertEquals(MoreLikeThisState.Idle, awaitItem())
            vm.processIntent(MoreLikeThisIntent.Load(42))
            assertEquals(MoreLikeThisState.Loading, awaitItem())
            val err = awaitItem()
            assertTrue(err is MoreLikeThisState.Error)
            assertEquals(
                com.tcohen.moviesapp.util.ApiError.NO_CONNECTION.message,
                (err as MoreLikeThisState.Error).message
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Retry (after Error) ────────────────────────────────────────────────────

    @Test
    fun `Load after Error transitions back through Loading then to Success`() = runTest {
        coEvery { repository.getSimilarMovies(42) } returnsMany listOf(
            NetworkResult.Error("Transient failure"),
            NetworkResult.Success(listOf(fakeMovie(id = 1)))
        )

        val vm = createViewModel()
        vm.state.test {
            assertEquals(MoreLikeThisState.Idle, awaitItem())
            vm.processIntent(MoreLikeThisIntent.Load(42))
            assertEquals(MoreLikeThisState.Loading, awaitItem())
            val firstErr = awaitItem()
            assertTrue(firstErr is MoreLikeThisState.Error)
            // User retries.
            vm.processIntent(MoreLikeThisIntent.Load(42))
            assertEquals(MoreLikeThisState.Loading, awaitItem())
            val success = awaitItem()
            assertTrue(success is MoreLikeThisState.Success)
            assertEquals(listOf(fakeMovie(id = 1)), (success as MoreLikeThisState.Success).movies)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── OpenDetail (effect) ────────────────────────────────────────────────────

    @Test
    fun `OpenDetail emits a NavigateToDetail effect with the movie id`() = runTest {
        val vm = createViewModel()
        vm.effects.test {
            vm.processIntent(MoreLikeThisIntent.OpenDetail(movieId = 99))
            val effect = awaitItem()
            assertTrue(effect is MoreLikeThisEffect.NavigateToDetail)
            assertEquals(99, (effect as MoreLikeThisEffect.NavigateToDetail).movieId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Default state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state is Idle`() {
        assertEquals(MoreLikeThisState.Idle, createViewModel().state.value)
    }
}
