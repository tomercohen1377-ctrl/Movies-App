package com.tcohen.moviesapp.data.remote.server

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tcohen.moviesapp.BuildConfig
import com.tcohen.moviesapp.data.auth.AuthRepository
import com.tcohen.moviesapp.data.remote.server.api.ServerApiService
import com.tcohen.moviesapp.data.remote.server.api.ServerAuthService
import com.tcohen.moviesapp.data.remote.server.api.safeServerApiCall
import com.tcohen.moviesapp.data.user.UserIdProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FavoritesServerJourneyTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var userIdProvider: UserIdProvider
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var serverApiService: ServerApiService
    @Inject lateinit var serverAuthService: ServerAuthService

    private val testMovieId = 550

    @Before
    fun setUp() {
        hiltRule.inject()

        runBlocking {
            userIdProvider.clear()
        }
    }

    @Test
    fun fullFavoritesFlow_registerToggleListRemove() = runBlocking {
        assumeTrue(
            "Server smoke test is opt‑in; flip SERVER_SMOKE_TEST_ENABLED to true",
            BuildConfig.SERVER_SMOKE_TEST_ENABLED
        )

        val snapshot = authRepository.signUpIfNeeded()
        assertNotNull("AuthSnapshot should be non-null after register", snapshot)
        assertTrue("Snapshot.userId should be a canonical UUID",
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$").matches(snapshot!!.userId))
        assertTrue("accessToken looks like a JWT (3‑part, period‑separated)",
            snapshot.accessToken.count { it == '.' } == 2)

        val addResult = safeServerApiCall {
            serverApiService.addFavorite(snapshot.userId, testMovieId)
        }
        assertTrue("addFavorite should succeed, was $addResult",
            addResult is com.tcohen.moviesapp.util.NetworkResult.Success)
        val created = (addResult as com.tcohen.moviesapp.util.NetworkResult.Success).data.created
        assertTrue("addFavorite response should report created=true, was $created", created)

        val listResult = safeServerApiCall {
            serverApiService.getFavorites(snapshot.userId)
        }
        assertTrue("getFavorites should succeed, was $listResult",
            listResult is com.tcohen.moviesapp.util.NetworkResult.Success)
        val list = (listResult as com.tcohen.moviesapp.util.NetworkResult.Success).data
        assertTrue("Expected movieId $testMovieId to appear in the list, got $list",
            list.any { it.movieId == testMovieId })

        val removeResponse: Response<Unit> = serverApiService.removeFavorite(
            snapshot.userId, testMovieId
        )
        assertTrue("DELETE should return 204, was ${removeResponse.code()}",
            removeResponse.isSuccessful)

        val listAfterRemove = safeServerApiCall {
            serverApiService.getFavorites(snapshot.userId)
        }
        assertTrue(listAfterRemove is com.tcohen.moviesapp.util.NetworkResult.Success)
        val list2 = (listAfterRemove as com.tcohen.moviesapp.util.NetworkResult.Success).data
        assertTrue(
            "Expected movieId $testMovieId to NOT appear after remove, got $list2",
            list2.none { it.movieId == testMovieId }
        )
    }
}
