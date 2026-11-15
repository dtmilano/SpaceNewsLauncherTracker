package com.example.spacenewslaunchertracker

import com.example.spacenewslaunchertracker.ui.components.Countdown
import com.example.spacenewslaunchertracker.ui.components.countdownBetween
import com.example.spacenewslaunchertracker.ui.components.toPlainString
import com.example.spacenewslaunchertracker.ui.components.toSpokenString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The launch clock's arithmetic, checked at the moments that actually break clocks.
 *
 * None of this needs a device, an emulator or a second of waiting, because
 * `countdownBetween` is a pure function of `(target, now)` — which is the entire reason the
 * composable does not call `Instant.now()` itself.
 */
class LaunchClockTest {

    private val now: Instant = Instant.parse("2026-11-12T10:00:00Z")

    @Test
    fun `no target is reported as to be announced`() {
        val countdown = countdownBetween(target = null, now = now)
        assertEquals(Countdown.ToBeAnnounced, countdown)
        assertTrue(countdown.isToBeAnnounced)
    }

    @Test
    fun `counts down whole fields`() {
        val target = now
            .plus(2, ChronoUnit.DAYS)
            .plus(3, ChronoUnit.HOURS)
            .plusSeconds(4 * 60 + 5)

        val countdown = countdownBetween(target, now)

        assertFalse(countdown.hasLiftedOff)
        assertEquals(2L, countdown.days)
        assertEquals(3, countdown.hours)
        assertEquals(4, countdown.minutes)
        assertEquals(5, countdown.seconds)
    }

    @Test
    fun `the instant of liftoff still counts down, not up`() {
        val countdown = countdownBetween(target = now, now = now)

        assertFalse(countdown.hasLiftedOff)
        assertEquals(0L, countdown.days)
        assertEquals(0, countdown.seconds)
    }

    @Test
    fun `a passed launch counts up and does not go negative`() {
        val target = now.minusSeconds(3 * 3600 + 25 * 60 + 9)

        val countdown = countdownBetween(target, now)

        assertTrue(countdown.hasLiftedOff)
        assertEquals(3, countdown.hours)
        assertEquals(25, countdown.minutes)
        assertEquals(9, countdown.seconds)
    }

    @Test
    fun `truncation is symmetric around liftoff`() {
        // Half a second either side of ignition should read as zero, not as one second on
        // the late side — the bug you get from Duration.getSeconds(), which floors.
        val before = countdownBetween(now.plusMillis(500), now)
        val after = countdownBetween(now.minusMillis(500), now)

        assertEquals(0, before.seconds)
        assertEquals(0, after.seconds)
        assertFalse(before.hasLiftedOff)
        assertTrue(after.hasLiftedOff)
    }

    @Test
    fun `the day field is dropped below one day`() {
        val sameDay = countdownBetween(now.plus(5, ChronoUnit.HOURS), now)
        val nextDay = countdownBetween(now.plus(30, ChronoUnit.HOURS), now)

        assertEquals("T-05:00:00", sameDay.toPlainString())
        assertEquals("T-01d:06h:00m", nextDay.toPlainString())
    }

    @Test
    fun `the plain rendering is fixed width within a mode`() {
        val a = countdownBetween(now.plusSeconds(9), now).toPlainString()
        val b = countdownBetween(now.plusSeconds(59_999), now).toPlainString()

        // A ticking clock that changes width jitters horizontally; the digits are padded
        // so every value in the same mode occupies the same number of characters.
        assertEquals(a.length, b.length)
    }

    @Test
    fun `the spoken form is a sentence, not a glyph soup`() {
        val spoken = countdownBetween(now.plus(26, ChronoUnit.HOURS), now).toSpokenString()

        assertTrue(spoken, spoken.startsWith("Launching in"))
        assertTrue(spoken, spoken.contains("1 days"))
        assertFalse(spoken, spoken.contains("T-"))

        val past = countdownBetween(now.minusSeconds(90), now).toSpokenString()
        assertTrue(past, past.startsWith("Lifted off"))
        assertTrue(past, past.endsWith("ago"))
    }
}
