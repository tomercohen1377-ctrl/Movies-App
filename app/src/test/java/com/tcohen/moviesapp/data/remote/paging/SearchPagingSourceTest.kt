package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingSource
import com.tcohen.moviesapp.data.remote.api.SafeApiCaller
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeMovieListResponse
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [SearchPagingSource].
 *
 * The source's offline + exception handling is delegated to [SafeApiCaller]; the
 * only distinguishing logic on top is that an [ApiError.NO_CONNECTION] result gets
 * mapped to [com.tcohen.moviesapp.util.NetworkUnavailableException] so Paging's
 * `Error` state surfaces the right copy in the UI footer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchPagingSourceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val apiService: TmdbApiService = mockk()
    private val networkMonitor: NetworkMonitor = mockk()
    private val safeApiCaller = SafeApiCaller(networkMonitor)

    private fun createSource(query: String = "dune") = SearchPagingSource(
        safeApiCaller = safeApiCaller,
        apiService = apiService,
        query = query
    )

    private val defaultParams = PagingSource.LoadParams.Refresh(
        key = null, loadSize = 20, placeholdersEnabled = false
    )

    // ── Success path ───────────────────────────────────────────────────────────

    @Test
    fun `online success returns mapped movies with null prevKey on page 1`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.searchMovies(query = "dune", page = 1) } returns
            fakeMovieListResponse(page = 1, totalPages = 3, count = 5)

        val result = createSource().load(defaultParams)
                as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.prevKey)
        assertEquals(2, result.nextKey)
        assertEquals(5, result.data.size)
    }

    @Test
    fun `online success on page 2 has correct prevKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.searchMovies(query = "dune", page = 2) } returns
            fakeMovieListResponse(page = 2, totalPages = 3, count = 5)

        val params = PagingSource.LoadParams.Refresh(
            key = 2, loadSize = 20, placeholdersEnabled = false
        )
        val result = createSource().load(params)
                as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(1, result.prevKey)
        assertEquals(3, result.nextKey)
    }

    @Test
    fun `online success on last page returns null nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.searchMovies(query = "dune", page = 3) } returns
            fakeMovieListResponse(page = 3, totalPages = 3, count = 2)

        val params = PagingSource.LoadParams.Refresh(
            key = 3, loadSize = 20, placeholdersEnabled = false
        )
        val result = createSource().load(params)
                as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.nextKey)
        assertEquals(2, result.data.size)
    }

    // ── Offline path (via safeApiCaller) ───────────────────────────────────────

    @Test
    fun `offline surfaces as NetworkUnavailableException, NOT generic Exception`() = runTest {
        // SafeApiCaller's pre-check kicks in: it short-circuits without calling the API.
        every { networkMonitor.isCurrentlyOnline() } returns false

        val result = createSource().load(defaultParams)

        assertTrue(result is PagingSource.LoadResult.Error)
        val error = (result as PagingSource.LoadResult.Error).throwable
        assertTrue(
            "expected NetworkUnavailableException, got ${error::class.simpleName}",
            error is com.tcohen.moviesapp.util.NetworkUnavailableException
        )
        // Crucial: the API was NOT called — saves a useless network request.
        io.mockk.coVerify(exactly = 0) { apiService.searchMovies(any(), any(), any(), any()) }
    }

    // ── Error mapping ──────────────────────────────────────────────────────────

    @Test
    fun `any non-NO_CONNECTION Error becomes a generic Exception with the message`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val serverMessage = "Invalid API key."
        // Bypass the throws-by-network-stack here — we want the safeApiCaller
        // happy-path to receive the Error from the block. Easiest is to mock the
        // API directly to throw the exception we want; safeApiCaller wraps it.
        coEvery { apiService.searchMovies(any(), any(), any(), any()) } throws
            RuntimeException(serverMessage)

        val result = createSource().load(defaultParams)

        assertTrue(result is PagingSource.LoadResult.Error)
        val error = (result as PagingSource.LoadResult.Error).throwable
        assertTrue(
            "expected generic Exception, got ${error::class.simpleName}",
            error !is com.tcohen.moviesapp.util.NetworkUnavailableException
        )
        assertEquals(serverMessage, error.message)
    }

    // ── Fragmented API call assertion ─────────────────────────────────────────

    @Test
    fun `page number flows through to api call as 1 by default`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.searchMovies(query = "dune", page = 1) } returns
            fakeMovieListResponse(page = 1, totalPages = 1, count = 1)

        createSource().load(defaultParams)

        io.mockk.coVerify(exactly = 1) {
            apiService.searchMovies(query = "dune", page = 1, includeAdult = false, language = "en-US")
        }
    }

    // ── getRefreshKey ─────────────────────────────────────────────────────────

    @Test
    fun `getRefreshKey returns anchor plus one when prevKey exists`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true

        val state = mockk<androidx.paging.PagingState<Int, Movie>>()
        val anchorPage = mockk<PagingSource.LoadResult.Page<Int, Movie>>()
        every { state.anchorPosition } returns 7
        every { state.closestPageToPosition(7) } returns anchorPage
        every { anchorPage.prevKey } returns 1
        every { anchorPage.nextKey } returns 3

        assertEquals(2, createSource().getRefreshKey(state))
    }

    @Test
    fun `getRefreshKey returns nextKey minus one when prevKey is null`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true

        val state = mockk<androidx.paging.PagingState<Int, Movie>>()
        val anchorPage = mockk<PagingSource.LoadResult.Page<Int, Movie>>()
        every { state.anchorPosition } returns 7
        every { state.closestPageToPosition(7) } returns anchorPage
        every { anchorPage.prevKey } returns null
        every { anchorPage.nextKey } returns 4

        assertEquals(3, createSource().getRefreshKey(state))
    }

    @Test
    fun `getRefreshKey returns null when no anchor is set`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        val state = mockk<androidx.paging.PagingState<Int, Movie>>()
        every { state.anchorPosition } returns null

        assertNull(createSource().getRefreshKey(state))
    }
}
