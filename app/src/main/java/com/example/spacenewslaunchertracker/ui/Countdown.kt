package com.example.spacenewslaunchertracker.ui

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

/** A launch clock reading, already split into the digits a countdown row displays. */
data class LaunchClock(
    val sign: String,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
    val caption: String,
    val isPast: Boolean,
) {
    companion object {
        /** Shown when the provider hasn't committed to a date, so there is nothing to count. */
        val ToBeAnnounced = LaunchClock(
            sign = "T-",
            days = -1,
            hours = -1,
            minutes = -1,
            seconds = -1,
            caption = "TO BE ANNOUNCED",
            isPast = false,
        )
    }

    val isUnknown: Boolean get() = days < 0
}

/**
 * Builds the T-minus reading for [liftoff] relative to [now].
 *
 * A liftoff in the past counts up as `T+` rather than going negative, which is how a real
 * launch clock behaves once the vehicle has left the pad.
 */
fun launchClock(liftoff: Instant?, now: Instant, isDateFirm: Boolean): LaunchClock {
    if (liftoff == null || !isDateFirm) return LaunchClock.ToBeAnnounced

    val totalSeconds = (liftoff.epochSecond - now.epochSecond)
    val isPast = totalSeconds < 0
    val remaining = totalSeconds.absoluteValue
    return LaunchClock(
        sign = if (isPast) "T+" else "T-",
        days = remaining / 86_400,
        hours = (remaining % 86_400) / 3_600,
        minutes = (remaining % 3_600) / 60,
        seconds = remaining % 60,
        caption = if (isPast) "LIFTOFF HAS PASSED" else "UNTIL LIFTOFF",
        isPast = isPast,
    )
}

private val LAUNCH_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy · HH:mm 'UTC'").withZone(ZoneOffset.UTC)

private val ARTICLE_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm 'UTC'").withZone(ZoneOffset.UTC)

/** Human-readable launch date in UTC, or the "to be announced" wording. */
fun formatLaunchDate(liftoff: Instant?, isDateFirm: Boolean): String = when {
    liftoff == null -> "To be announced"
    !isDateFirm -> "${LAUNCH_DATE_FORMAT.format(liftoff)} (not yet confirmed)"
    else -> LAUNCH_DATE_FORMAT.format(liftoff)
}

fun formatPublished(published: Instant?): String =
    published?.let { ARTICLE_DATE_FORMAT.format(it) } ?: "Date unknown"
