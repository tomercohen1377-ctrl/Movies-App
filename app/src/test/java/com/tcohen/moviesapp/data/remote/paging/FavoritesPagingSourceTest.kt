package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.tcohen.moviesapp.data.local.LocalMovieDataSource
import com.tcohen.moviesapp.data.remote.TmdbRemoteDataSource
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeMovie
import com.tcohen.moviesapp.fakeMovieListResponse
import com.tcohen.moviesapp.util.NetworkMonitor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesPagingSourceTest {

    private val remoteDataSource: TmdbRemoteDataSource = mockk()
    private val localDataSource: LocalMovieDataSource = mockk()
    private val networkMonitor: NetworkMonitor = mockk()

    private val pagingConfig = PagingConfig(pageSize = 20, enablePlaceholders = false)

    private fun createSource() = FavoritesPagingSource(
        remoteDataSource = remoteDataSource,
        localDataSource  = localDataSource,
        networkMonitor   = networkMonitor
    )

    // ── Online path ───────────────────────────────────────────────────────────

    @Test
    fun `online - first page has null prevKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { remoteDataSource.getFavoriteMovies(1) } returns
            fakeMovieListResponse(page = 1, totalPages = 3)

        val pager = TestPager(pagingConfig, createSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.prevKey)
    }

    @Test
    fun `online - first page has correct nextKey when more pages exist`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { remoteDataSource.getFavoriteMovies(1) } returns
            fakeMovieListResponse(page = 1, totalPages = 3)

        val pager = TestPager(pagingConfig, createSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(2, result.nextKey)
    }

    @Test
    fun `online - last page has null nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { remoteDataSource.getFavoriteMovies(2) } returns
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
        coEvery { remoteDataSource.getFavoriteMovies(1) } returns
            fakeMovieListResponse(page = 1, totalPages = 1, count = 5)

        val pager = TestPager(pagingConfig, createSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(5, result.data.size)
    }

    @Test
    fun `online - API error returns LoadResult Error`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { remoteDataSource.getFavoriteMovies(any()) } throws
            RuntimeException("Network failure")

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Error)
    }

    // ── Offline path ──────────────────────────────────────────────────────────

    @Test
    fun `offline - returns cached favorites from SQLDelight`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val movies = (1..5).map { fakeMovie(id = it) }
        coEvery { localDataSource.getFavoritesPaged(limit = 21, offset = 0) } returns movies

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(5, result.data.size)
    }

    @Test
    fun `offline - first page has null prevKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        coEvery { localDataSource.getFavoritesPaged(limit = 21, offset = 0) } returns
            (1..5).map { fakeMovie(id = it) }

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.prevKey)
    }

    @Test
    fun `offline - has nextKey when more than PAGE_SIZE items returned`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val movies = (1..21).map { fakeMovie(id = it) }
        coEvery { localDataSource.getFavoritesPaged(limit = 21, offset = 0) } returns movies

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(2, result.nextKey)
        assertEquals(20, result.data.size)
    }

    @Test
    fun `offline - no favorites returns empty page with null nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        coEvery { localDataSource.getFavoritesPaged(limit = 21, offset = 0) } returns emptyList()

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
        coEvery { localDataSource.getFavoritesPaged(limit = 21, offset = 20) } returns
            (1..3).map { fakeMovie(id = it) }

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Append(key = 2, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(3, result.data.size)
        assertEquals(1, result.prevKey)
    }
}
