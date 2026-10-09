package dev.milinko.movielens.util

import dev.milinko.movielens.MainDispatcherRule
import dev.milinko.movielens.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ErrorHandlingTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `safeApiCall wraps failures into Result`() = runTest {
        val result = safeApiCall<Int> { throw IOException("offline") }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(5, safeApiCall { 5 }.getOrNull())
    }

    @Test
    fun `safeApiCall does not swallow cancellation`() = runTest {
        try {
            safeApiCall<Int> { throw CancellationException("typed another letter") }
            fail("CancellationException must be rethrown")
        } catch (e: CancellationException) {
            // expected - a cancelled search must not be shown as an error
        }
    }

    @Test
    fun `network and http errors map to friendly resource messages`() {
        assertEquals("res:${R.string.error_network}", friendlyMessage(IOException("timeout"), "fallback"))
        assertEquals("res:${R.string.error_forbidden}", friendlyMessage(http(401), "fallback"))
        assertEquals("res:${R.string.error_not_found}", friendlyMessage(http(404), "fallback"))
        assertEquals("res:${R.string.error_too_many_requests}", friendlyMessage(http(429), "fallback"))
        assertEquals("res:${R.string.error_server}", friendlyMessage(http(503), "fallback"))
        assertEquals("fallback", friendlyMessage(http(409), "fallback"))
    }

    @Test
    fun `only app-written messages are shown, raw exception text never`() {
        assertEquals("Korisničko ime je zauzeto", friendlyMessage(IllegalStateException("Korisničko ime je zauzeto"), "fallback"))
        assertEquals("Pokušaj ponovo", friendlyMessage(TransientServiceException("Pokušaj ponovo"), "fallback"))
        assertEquals("fallback", friendlyMessage(RuntimeException("java.lang.NullPointerException at ..."), "fallback"))
    }
}
