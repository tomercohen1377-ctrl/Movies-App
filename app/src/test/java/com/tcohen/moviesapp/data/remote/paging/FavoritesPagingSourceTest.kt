package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.tcohen.moviesapp.data.local.dao.FavoriteDao
import com.tcohen.moviesapp.data.local.entity.FavoriteEntity
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeFavoriteEntity
import com.tcohen.moviesapp.fakeMovieListResponse
import com.tcohen.moviesapp.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesPagingSourceTest {

    private val apiService: TmdbApiService = mockk()
    private val favoriteDao: FavoriteDao = mockk()
    private val networkMonitor: NetworkMonitor = mockk()

    private val pagingConfig = PagingConfig(pageSize = 20, enablePlaceholders = false)

    private fun stubInsertAll() {
        // The online path now writes the page through to Room via insertAll.
        // Default behaviour in tests: nothing happens, but the call must be stubbed
        // so the mock doesn't fail.
        coJustRun { favoriteDao.insertAll(any()) }
    }

    private fun createSource() = FavoritesPagingSource(
        apiService = apiService,
        favoriteDao = favoriteDao,
        networkMonitor = networkMonitor,
        accountId = "me",
        sessionId = ""
    )

    // ── Online path ───────────────────────────────────────────────────────────

    @Test
    fun `online - first page has null prevKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        stubInsertAll()
        coEvery { apiService.getFavoriteMovies(any(), any(), 1) } returns
            fakeMovieListResponse(page = 1, totalPages = 3)

        val pager = TestPager(pagingConfig, createSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.prevKey)
    }

    @Test
    fun `online - first page has correct nextKey when more pages exist`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        stubInsertAll()
        coEvery { apiService.getFavoriteMovies(any(), any(), 1) } returns
            fakeMovieListResponse(page = 1, totalPages = 3)

        val pager = TestPager(pagingConfig, createSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(2, result.nextKey)
    }

    @Test
    fun `online - last page has null nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        stubInsertAll()
        coEvery { apiService.getFavoriteMovies(any(), any(), 2) } returns
            fakeMovieListResponse(page = 2, totalPages = 2)

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 2, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.nextKey)
    }

    @Test
    fun `online - movies are returned from response`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        stubInsertAll()
        coEvery { apiService.getFavoriteMovies(any(), any(), 1) } returns
            fakeMovieListResponse(page = 1, totalPages = 1, count = 5)

        val pager = TestPager(pagingConfig, createSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(5, result.data.size)
    }

    @Test
    fun `online - API error returns LoadResult Error`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        // No stubInsertAll() here — the API throws before insertAll is reached.
        coEvery { apiService.getFavoriteMovies(any(), any(), any()) } throws
            RuntimeException("Network failure")

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Error)
    }

    // ── Room write-through (regression fix for Detail-screen FAB) ─────────────

    @Test
    fun `online - first page is mirrored into Room via insertAll`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getFavoriteMovies(any(), any(), 1) } returns
            fakeMovieListResponse(page = 1, totalPages = 1, count = 5)
        val capturedEntities = mutableListOf<List<FavoriteEntity>>()
        coEvery { favoriteDao.insertAll(capture(capturedEntities)) } returns Unit

        TestPager(pagingConfig, createSource()).refresh()

        // 5 movies from the fake response must be inserted into Room.
        assertEquals(1, capturedEntities.size)
        assertEquals(5, capturedEntities.first().size)
    }

    @Test
    fun `online - successful fetch does not leave Room untouched (verifies insertAll was called)`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getFavoriteMovies(any(), any(), 1) } returns
            fakeMovieListResponse(page = 1, totalPages = 1, count = 3)

        TestPager(pagingConfig, createSource()).refresh()

        // Without the fix there would be no insertAll invocation at all.
        coVerify(exactly = 1) { favoriteDao.insertAll(any()) }
    }

    @Test
    fun `online - API error does NOT touch Room`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getFavoriteMovies(any(), any(), any()) } throws
            RuntimeException("Network failure")

        val source = createSource()
        source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
        )

        // Coordinator behaviour: failed fetches must not partially write to cache.
        coVerify(exactly = 0) { favoriteDao.insertAll(any()) }
    }

    // ── Offline path ──────────────────────────────────────────────────────────

    @Test
    fun `offline - returns cached favorites from Room`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val entities = (1..5).map { fakeFavoriteEntity(id = it) }
        // Returns 5 items + 0 extra (no next page)
        coEvery { favoriteDao.getFavoritesPaged(limit = 21, offset = 0) } returns entities

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(5, result.data.size)
    }

    @Test
    fun `offline - first page has null prevKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        coEvery { favoriteDao.getFavoritesPaged(limit = 21, offset = 0) } returns
            (1..5).map { fakeFavoriteEntity(id = it) }

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.prevKey)
    }

    @Test
    fun `offline - has nextKey when more than PAGE_SIZE items returned`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        // 21 items returned → hasNextPage = true
        val entities = (1..21).map { fakeFavoriteEntity(id = it) }
        coEvery { favoriteDao.getFavoritesPaged(limit = 21, offset = 0) } returns entities

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(2, result.nextKey)
        // Extra item is dropped from result data
        assertEquals(20, result.data.size)
    }

    @Test
    fun `offline - no favorites returns empty page with null nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        coEvery { favoriteDao.getFavoritesPaged(limit = 21, offset = 0) } returns emptyList()

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(0, result.data.size)
        assertNull(result.nextKey)
    }

    @Test
    fun `offline - correct offset used for page 2`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        // Page 2 → offset = (2-1) * 20 = 20
        coEvery { favoriteDao.getFavoritesPaged(limit = 21, offset = 20) } returns
            (1..3).map { fakeFavoriteEntity(id = it) }

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Append(key = 2, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(3, result.data.size)
        assertEquals(1, result.prevKey) // page 2 → prevKey = 1
    }
}
