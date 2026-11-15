package com.example.spacenewslaunchertracker.data

import java.time.Instant

/**
 * A single upcoming launch, flattened from Launch Library 2 into the shape the UI needs.
 *
 * [liftoff] is null whenever the provider has not committed to a real date/time yet; see
 * [isDateFirm] for the "to be announced" case where a timestamp exists but is only accurate
 * to the day/month/year.
 */
data class UpcomingLaunch(
    val id: String,
    val name: String,
    val missionName: String?,
    val missionDescription: String?,
    val provider: String?,
    val status: String?,
    val statusIsGo: Boolean,
    val rocket: String?,
    val padName: String?,
    val padLocation: String?,
    val latitude: Double?,
    val longitude: Double?,
    val imageUrl: String?,
    val liftoff: Instant?,
    val isDateFirm: Boolean,
) {
    /** True when we can show a real ticking clock rather than "to be announced". */
    val hasCountdown: Boolean get() = liftoff != null && isDateFirm

    val coordinates: Pair<Double, Double>?
        get() = if (latitude != null && longitude != null) latitude to longitude else null
}

/** A recent article from the Spaceflight News API. */
data class NewsArticle(
    val id: String,
    val title: String,
    val summary: String?,
    val newsSite: String?,
    val url: String,
    val imageUrl: String?,
    val publishedAt: Instant?,
)
