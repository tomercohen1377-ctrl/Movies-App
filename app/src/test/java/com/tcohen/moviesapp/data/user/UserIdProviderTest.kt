package com.tcohen.moviesapp.data.user

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserIdProviderTest {

    private lateinit var editor: SharedPreferences.Editor
    private lateinit var prefs: SharedPreferences
    private lateinit var provider: TestableUserIdProvider

    private var storedUserId: String? = null
    private var mockedAndroidId: String? = "android-id"

    /** 32 lowercase hex chars — the format of the salted-hashed userId. */
    private val hex32Regex = Regex("^[0-9a-f]{32}$")

    /**
     * Subclass that pins the ANDROID_ID source to a mutable field
     * so tests can flip it without trying to mock Android's final
     * [android.provider.Settings.Secure] class.
     */
    private open class TestableUserIdProvider(
        context: Context,
        prefs: SharedPreferences,
    ) : UserIdProvider(context, prefs) {
        override fun resolveAndroidId(): String? = resolvedAndroidId
        companion object {
            var resolvedAndroidId: String? = "android-id"
        }
    }

    @Before
    fun setUp() {
        storedUserId = null
        mockedAndroidId = "android-id"
        TestableUserIdProvider.resolvedAndroidId = mockedAndroidId
        editor = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { prefs.edit()} returns editor
        every {prefs.getString(any<String>(), any())} answers { storedUserId }
        every {editor.putString(any<String>(), any<String>())} answers {
            storedUserId = secondArg()
            editor
        }
        every {editor.remove(any<String>())} answers {
            storedUserId = null
            editor
        }
        every {editor.clear()} answers {
            storedUserId = null
            editor
        }
        every {editor.commit()} returns true

        provider = TestableUserIdProvider(
            context = mockk<Context>(relaxed = true),
            prefs = prefs,
        )
    }

    @Test
    fun `generateIfMissing derives a 32-char hex userId from ANDROID_ID`() = runTest {
        val userId = provider.generateIfMissing()
        assertTrue(
            "Expected 32-char lowercase hex, got '$userId'",
            hex32Regex.matches(userId),
        )
        assertEquals(userId, provider.get())
        verify { editor.putString("userId", userId) }
    }

    @Test
    fun `generateIfMissing returns the same userId on subsequent calls`() = runTest {
        val first = provider.generateIfMissing()
        val second = provider.generateIfMissing()
        assertEquals(first, second)
    }

    @Test
    fun `generateIfMissing is deterministic across cache wipes for the same device`() = runTest {
        val first = provider.generateIfMissing()
        provider.clear()
        val second = provider.generateIfMissing()
        assertEquals(
            "Same ANDROID_ID must produce same userId after the cache is wiped",
            first,
            second,
        )
    }

    @Test
    fun `generateIfMissing produces different userIds for different devices`() = runTest {
        val first = provider.generateIfMissing()
        TestableUserIdProvider.resolvedAndroidId = "different-android-id"
        provider.clear()
        val second = provider.generateIfMissing()
        assertTrue(
            "Two devices must produce different userIds (got $first vs $second)",
            first != second,
        )
    }

    @Test
    fun `set overrides the derived value and persists it`() = runTest {
        provider.generateIfMissing()
        val custom = "c".repeat(32)
        provider.set(custom)
        assertEquals(custom, provider.get())
    }

    @Test
    fun `get throws when no userId is persisted`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { provider.get() }
        }
    }

    @Test
    fun `getBlocking returns the persistent value`() = runTest {
        provider.generateIfMissing()
        val blocking = provider.getBlocking()
        assertTrue(hex32Regex.matches(blocking))
    }

    @Test
    fun `getBlocking throws synchronously when no userId is persisted`() {
        assertThrows(IllegalStateException::class.java) { provider.getBlocking() }
    }

    @Test
    fun `clear removes the userId so the next get throws`() = runTest {
        provider.generateIfMissing()
        provider.clear()
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { provider.get() }
        }
    }

    @Test
    fun `set rejects blank values`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { provider.set("") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { provider.set("   ") }
        }
    }

    @Test
    fun `USER_ID_PREFS_FILE matches the UtilModule wiring`() {
        assertEquals("server_user_id", UserIdProvider.USER_ID_PREFS_FILE)
    }
}
