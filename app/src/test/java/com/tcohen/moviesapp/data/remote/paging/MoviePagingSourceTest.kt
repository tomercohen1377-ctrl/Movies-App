package com.tcohen.moviesapp.data.remote.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import com.tcohen.moviesapp.data.local.dao.MovieDao
import com.tcohen.moviesapp.data.remote.api.TmdbApiService
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.fakeMovieEntity
import com.tcohen.moviesapp.fakeMovieListResponse
import com.tcohen.moviesapp.util.NetworkMonitor
import com.tcohen.moviesapp.util.NetworkUnavailableException
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoviePagingSourceTest {

    private val apiService: TmdbApiService = mockk()
    private val movieDao: MovieDao = mockk()
    private val networkMonitor: NetworkMonitor = mockk()

    private val pagingConfig = PagingConfig(pageSize = 20, enablePlaceholders = false)

    @Before
    fun setUp() {
        coJustRun { movieDao.insertAll(any()) }
    }

    private fun createSource(category: Category = Category.UPCOMING) =
        MoviePagingSource(apiService, movieDao, category, networkMonitor)

    // ── Online path ──────────────────────────────────────────────────��────────

    @Test
    fun `online - first page loaded with correct nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getUpcomingMovies(1) } returns fakeMovieListResponse(page = 1, totalPages = 5)

        val pager = TestPager(pagingConfig, createSource())
        val result = pager.refresh() as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(20, result.data.size)
        assertNull(result.prevKey)
        assertEquals(2, result.nextKey)
    }

    @Test
    fun `online - last page has null nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getUpcomingMovies(3) } returns fakeMovieListResponse(page = 3, totalPages = 3)

        val pager = TestPager(pagingConfig, createSource())
        pager.refresh() // page 1

        // Simulate jumping to page 3 (last)
        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 3, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.nextKey)
        assertEquals(2, result.prevKey)
    }

    @Test
    fun `online - correct API called for TOP_RATED category`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getTopRatedMovies(1) } returns fakeMovieListResponse()

        val pager = TestPager(pagingConfig, createSource(Category.TOP_RATED))
        pager.refresh()

        io.mockk.coVerify { apiService.getTopRatedMovies(1) }
    }

    @Test
    fun `online - correct API called for NOW_PLAYING category`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getNowPlayingMovies(1) } returns fakeMovieListResponse()

        val pager = TestPager(pagingConfig, createSource(Category.NOW_PLAYING))
        pager.refresh()

        io.mockk.coVerify { apiService.getNowPlayingMovies(1) }
    }

    @Test
    fun `online - movies are cached to Room after network load`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getUpcomingMovies(1) } returns fakeMovieListResponse()

        val pager = TestPager(pagingConfig, createSource())
        pager.refresh()

        io.mockk.coVerify { movieDao.insertAll(any()) }
    }

    @Test
    fun `online - API error returns LoadResult Error`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns true
        coEvery { apiService.getUpcomingMovies(1) } throws RuntimeException("HTTP 500")

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Error)
    }

    // ── Offline path ──────────────────────────────────────────────────────────

    @Test
    fun `offline - returns cached data from Room`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val cachedEntities = (1..5).map { fakeMovieEntity(id = it, page = 1) }
        coEvery { movieDao.getMoviesByCategory(Category.UPCOMING.name) } returns cachedEntities
        coEvery { movieDao.getLastCachedPage(Category.UPCOMING.name) } returns 1

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(5, result.data.size)
        assertEquals(1, result.data[0].id)
    }

    @Test
    fun `offline - beyond cached pages returns NetworkUnavailableException`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        coEvery { movieDao.getMoviesByCategory(Category.UPCOMING.name) } returns emptyList()

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Error)
        assertTrue((result as PagingSource.LoadResult.Error).throwable is NetworkUnavailableException)
    }

    @Test
    fun `offline - cached page 1 has null prevKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val cachedEntities = (1..5).map { fakeMovieEntity(id = it, page = 1) }
        coEvery { movieDao.getMoviesByCategory(any()) } returns cachedEntities
        coEvery { movieDao.getLastCachedPage(any()) } returns 2

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertNull(result.prevKey)
    }

    @Test
    fun `offline - cached page before last page has nextKey`() = runTest {
        every { networkMonitor.isCurrentlyOnline() } returns false
        val cachedEntities = (1..5).map { fakeMovieEntity(id = it, page = 1) }
        coEvery { movieDao.getMoviesByCategory(any()) } returns cachedEntities
        coEvery { movieDao.getLastCachedPage(any()) } returns 3  // page 1 of 3

        val source = createSource()
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = 1, loadSize = 20, placeholdersEnabled = false)
        ) as PagingSource.LoadResult.Page<Int, Movie>

        assertEquals(2, result.nextKey)
    }
}
