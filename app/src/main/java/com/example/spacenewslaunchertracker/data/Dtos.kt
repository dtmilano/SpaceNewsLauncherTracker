package com.example.spacenewslaunchertracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant

/*
 * Wire types for Launch Library 2 (`/2.3.0/launches/upcoming/`) and the Spaceflight News
 * API (`/v4/articles/`).
 *
 * A few fields are typed as raw [JsonElement] on purpose: LL2 changed `image` from a bare
 * string (2.2.0) to an object (2.3.0), and reports pad coordinates as strings on some
 * records and numbers on others. Decoding them loosely and narrowing in [asDouble] /
 * [imageUrl] means an upstream shape change degrades one field instead of failing the
 * whole response.
 */

@Serializable
data class LaunchListResponse(
    val results: List<LaunchDto> = emptyList(),
)

@Serializable
data class LaunchDto(
    val id: String,
    val name: String = "Unnamed launch",
    val net: String? = null,
    @SerialName("net_precision") val netPrecision: JsonElement? = null,
    val status: StatusDto? = null,
    @SerialName("launch_service_provider") val provider: NamedDto? = null,
    val rocket: RocketDto? = null,
    val mission: MissionDto? = null,
    val pad: PadDto? = null,
    val image: JsonElement? = null,
)

@Serializable
data class StatusDto(val name: String? = null, val abbrev: String? = null)

@Serializable
data class NamedDto(val name: String? = null)

@Serializable
data class RocketDto(val configuration: RocketConfigDto? = null)

@Serializable
data class RocketConfigDto(
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
)

@Serializable
data class MissionDto(
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,
)

@Serializable
data class PadDto(
    val name: String? = null,
    val latitude: JsonElement? = null,
    val longitude: JsonElement? = null,
    val location: NamedDto? = null,
)

@Serializable
data class ArticleListResponse(
    val results: List<ArticleDto> = emptyList(),
)

@Serializable
data class ArticleDto(
    val id: Int,
    val title: String = "",
    val url: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("news_site") val newsSite: String? = null,
    val summary: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
)

/** Launch precisions at least this fine mean we can show a real second-by-second clock. */
private val FIRM_PRECISIONS = setOf("second", "minute", "hour")

private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.content?.toDoubleOrNull()

/** LL2 2.3.0 nests the URL under `image.image_url`; 2.2.0 returned the URL directly. */
private fun JsonElement?.imageUrl(): String? = when (this) {
    is JsonPrimitive -> content.takeIf { it.startsWith("http") }
    is JsonObject -> (this["image_url"] as? JsonPrimitive)?.content
        ?: (this["thumbnail_url"] as? JsonPrimitive)?.content
    else -> null
}

private fun JsonElement?.precisionName(): String? = when (this) {
    is JsonPrimitive -> content
    is JsonObject -> (this["name"] as? JsonPrimitive)?.content
    else -> null
}

private fun parseInstant(raw: String?): Instant? =
    raw?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun LaunchDto.toDomain(): UpcomingLaunch {
    val liftoff = parseInstant(net)
    val precision = netPrecision.precisionName()?.lowercase()
    val statusAbbrev = status?.abbrev?.uppercase()
    return UpcomingLaunch(
        id = id,
        name = name,
        missionName = mission?.name,
        missionDescription = mission?.description?.takeIf { it.isNotBlank() },
        provider = provider?.name,
        status = status?.name,
        statusIsGo = statusAbbrev == "GO",
        rocket = rocket?.configuration?.fullName ?: rocket?.configuration?.name,
        padName = pad?.name,
        padLocation = pad?.location?.name,
        latitude = pad?.latitude.asDouble(),
        longitude = pad?.longitude.asDouble(),
        imageUrl = image.imageUrl(),
        liftoff = liftoff,
        // No precision field at all still counts as firm: LL2 omits it on well-known launches.
        isDateFirm = liftoff != null &&
            statusAbbrev !in setOf("TBD", "TBC") &&
            (precision == null || precision in FIRM_PRECISIONS),
    )
}

fun ArticleDto.toDomain(): NewsArticle = NewsArticle(
    id = id.toString(),
    title = title,
    summary = summary?.takeIf { it.isNotBlank() },
    newsSite = newsSite,
    url = url,
    imageUrl = imageUrl,
    publishedAt = parseInstant(publishedAt),
)
