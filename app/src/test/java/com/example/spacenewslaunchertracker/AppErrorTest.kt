package com.example.spacenewslaunchertracker

import com.example.spacenewslaunchertracker.data.AppError
import com.example.spacenewslaunchertracker.data.ErrorKind
import com.example.spacenewslaunchertracker.data.HttpFailure
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

/**
 * The failure-to-sentence mapping.
 *
 * Worth testing because it is the one piece of the app a user reads at their least patient
 * moment, and because the two judgements it makes — "can retrying help?" and "is there an
 * action?" — drive whether the UI offers buttons. Getting those wrong means either a retry
 * button that cannot possibly work, or no way forward when there was one.
 */
class AppErrorTest {

    private val at: Instant = Instant.parse("2026-11-13T09:55:07Z")
    private val url = "https://ll.thespacedevs.com/2.3.0/launches/upcoming/?limit=30"
    private val source = "Launch Library 2"

    private fun error(cause: Throwable) = AppError.from(cause, source, url, at)

    @Test
    fun `no dns means offline, and says what to check`() {
        val e = error(UnknownHostException("ll.thespacedevs.com"))

        assertEquals(ErrorKind.Offline, e.kind)
        assertTrue(e.canRetry)
        assertTrue(e.action!!, e.action!!.contains("Wi-Fi"))
        // The summary is for someone who does not care whose fault it is.
        assertFalse(e.summary, e.summary.contains("Exception"))
        assertFalse(e.summary, e.summary.contains("HTTP"))
    }

    @Test
    fun `a timeout is distinct from being offline`() {
        val e = error(SocketTimeoutException("timeout"))

        assertEquals(ErrorKind.Timeout, e.kind)
        assertTrue(e.canRetry)
    }

    @Test
    fun `a rate limit explains the budget and quotes the server's own delay`() {
        val e = error(HttpFailure(code = 429, source = source, retryAfterSeconds = 120))

        assertEquals(ErrorKind.RateLimited, e.kind)
        assertEquals(120, e.retryAfterSeconds)
        assertTrue(e.canRetry)
        assertTrue(e.action!!, e.action!!.contains("2 minutes"))
        assertTrue(e.detail, e.detail.contains("15 anonymous requests"))
    }

    @Test
    fun `a rate limit without a Retry-After still gives usable advice`() {
        val e = error(HttpFailure(code = 429, source = source))

        assertNull(e.retryAfterSeconds)
        assertTrue(e.action!!, e.action!!.contains("few minutes"))
    }

    @Test
    fun `server faults are retryable and client faults are not`() {
        assertEquals(ErrorKind.ServerError, error(HttpFailure(503, source)).kind)
        assertTrue(error(HttpFailure(503, source)).canRetry)

        val notFound = error(HttpFailure(404, source))
        assertEquals(ErrorKind.NotFound, notFound.kind)
        assertFalse(notFound.canRetry)
        assertNull("nothing a user can do about a retired endpoint", notFound.action)

        val rejected = error(HttpFailure(403, source))
        assertEquals(ErrorKind.Rejected, rejected.kind)
        assertFalse(rejected.canRetry)
    }

    @Test
    fun `an unreadable response is not worth retrying`() {
        val e = error(SerializationException("Unexpected JSON token"))

        assertEquals(ErrorKind.BadData, e.kind)
        assertFalse("retrying a format change just fails again", e.canRetry)
        assertNull(e.action)
    }

    @Test
    fun `an unrecognised failure still produces all three levels`() {
        val e = error(IllegalStateException("boom"))

        assertEquals(ErrorKind.Unknown, e.kind)
        assertTrue(e.summary.isNotBlank())
        assertTrue(e.detail.isNotBlank())
        assertTrue(e.technical.isNotBlank())
    }

    @Test
    fun `technical details carry everything a bug report needs`() {
        val e = error(HttpFailure(code = 429, source = source, retryAfterSeconds = 90))

        val technical = e.technical
        assertTrue(technical, technical.contains("HTTP 429"))
        assertTrue(technical, technical.contains(url))
        assertTrue(technical, technical.contains("retry-after: 90s"))
        assertTrue(technical, technical.contains("HttpFailure"))
        assertTrue(technical, technical.contains("2026-11-13"))
        assertTrue(technical, technical.contains(source))
    }

    @Test
    fun `identical failures from both feeds merge into one panel`() {
        val a = AppError.from(UnknownHostException("a"), "Launch Library 2", url, at)
        val b = AppError.from(UnknownHostException("b"), "the Spaceflight News API", url, at)

        val merged = AppError.merge(listOf(a, b))

        assertEquals(1, merged.size)
        assertEquals(
            "Launch Library 2 and the Spaceflight News API",
            merged.single().source,
        )
        // Both sets of diagnostics survive the merge.
        assertTrue(merged.single().technical.contains("Launch Library 2"))
        assertTrue(merged.single().technical.contains("Spaceflight News"))
    }

    @Test
    fun `different failures stay separate`() {
        val offline = AppError.from(UnknownHostException("a"), "Launch Library 2", url, at)
        val limited = AppError.from(HttpFailure(429, "the Spaceflight News API"), "x", url, at)

        assertEquals(2, AppError.merge(listOf(offline, limited)).size)
    }

    @Test
    fun `a generic IO failure is treated as a transient network problem`() {
        val e = error(IOException("unexpected end of stream"))

        assertEquals(ErrorKind.Offline, e.kind)
        assertTrue(e.canRetry)
    }
}
