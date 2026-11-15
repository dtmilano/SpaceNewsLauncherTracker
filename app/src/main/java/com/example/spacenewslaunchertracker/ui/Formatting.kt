package com.example.spacenewslaunchertracker.ui

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
