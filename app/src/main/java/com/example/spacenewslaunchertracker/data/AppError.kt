package com.example.spacenewslaunchertracker.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A failure, described at three depths.
 *
 * A single `message: String` is not enough for a real app. The person looking at the screen
 * wants to know, in order and only as far as they care:
 *
 *  1. **What happened**, in one plain sentence — [summary].
 *  2. **Why, and what they can do about it** — [detail] and [action].
 *  3. **The raw facts**, for a bug report or for a developer — [technical].
 *
 * Splitting those apart is what lets the UI disclose them progressively instead of either
 * hiding the cause or dumping a stack trace on someone trying to read a launch time. The UI
 * that consumes this is `ui/ErrorPanel.kt`.
 *
 * [canRetry] is a judgement about whether tapping a button again could plausibly help, so
 * the UI does not offer a retry that is certain to fail the same way.
 */
@Immutable
data class AppError(
    val kind: ErrorKind,
    /** Which API this is about, in words a person recognises from the UI. */
    val source: String,
    /** One sentence, no jargon, no error codes. */
    val summary: String,
    /** A paragraph on the cause, still in plain language. Shown on request. */
    val detail: String,
    /** The single thing the user could do, or null when there is nothing to do but wait. */
    val action: String?,
    /** Raw facts: status, endpoint, exception, timestamp. Shown on request, copyable. */
    val technical: String,
    val canRetry: Boolean,
    /** For rate limits: roughly how long to wait, when the server told us. */
    val retryAfterSeconds: Int? = null,
) {
    companion object {
        /**
         * Builds an error from a thrown exception plus the context of the attempt.
         *
         * The context matters: "HTTP 429" is meaningless without knowing which of the two
         * APIs produced it and what was requested, and that is exactly what a user pasting
         * a bug report needs to include.
         */
        fun from(
            cause: Throwable,
            source: String,
            url: String,
            attemptedAt: Instant = Instant.now(),
        ): AppError {
            val base = classify(cause, source)
            return base.copy(
                technical = buildTechnical(cause, source, url, attemptedAt, base),
            )
        }

        private fun classify(cause: Throwable, source: String): AppError = when (cause) {
            is HttpFailure -> fromHttp(cause, source)

            is UnknownHostException -> AppError(
                kind = ErrorKind.Offline,
                source = source,
                summary = "No internet connection.",
                detail = "The device could not look up $source, which almost always " +
                    "means there is no working network connection rather than a problem " +
                    "with the API itself.",
                action = "Check Wi-Fi or mobile data, then try again.",
                technical = "",
                canRetry = true,
            )

            is ConnectException -> AppError(
                kind = ErrorKind.Offline,
                source = source,
                summary = "Couldn't connect to $source.",
                detail = "The network is reachable but the connection was refused or " +
                    "dropped. A captive portal — a hotel or café Wi-Fi sign-in page — " +
                    "does this, as does a VPN that has half-connected.",
                action = "Check the connection, then try again.",
                technical = "",
                canRetry = true,
            )

            is SocketTimeoutException -> AppError(
                kind = ErrorKind.Timeout,
                source = source,
                summary = "$source took too long to answer.",
                detail = "The request was sent but no reply arrived within 20 seconds. " +
                    "This is usually a slow connection, and occasionally the API being " +
                    "under load.",
                action = "Try again.",
                technical = "",
                canRetry = true,
            )

            is SerializationException -> AppError(
                kind = ErrorKind.BadData,
                source = source,
                summary = "$source sent data this app couldn't read.",
                detail = "The response arrived but did not match the shape this app " +
                    "expects. That normally means the API changed its format — a new " +
                    "version of the app would be needed, and retrying will not help.",
                action = null,
                technical = "",
                canRetry = false,
            )

            is IOException -> AppError(
                kind = ErrorKind.Offline,
                source = source,
                summary = "Network problem while contacting $source.",
                detail = "The connection failed part-way through. This is usually " +
                    "transient — a handover between Wi-Fi and mobile data will do it.",
                action = "Try again.",
                technical = "",
                canRetry = true,
            )

            else -> AppError(
                kind = ErrorKind.Unknown,
                source = source,
                summary = "Something went wrong loading $source.",
                detail = "The app hit an error it does not have a specific explanation " +
                    "for. The technical details below are worth including in a bug report.",
                action = "Try again.",
                technical = "",
                canRetry = true,
            )
        }

        private fun fromHttp(cause: HttpFailure, source: String): AppError = when (cause.code) {
            429 -> AppError(
                kind = ErrorKind.RateLimited,
                source = source,
                summary = "$source is rate limiting this app.",
                detail = "$source allows only about 15 anonymous requests an hour, and " +
                    "this app has used its share. Nothing is broken and no data is lost — " +
                    "the allowance refills on its own. Responses are cached for 15 " +
                    "minutes precisely to stay under the limit, but reinstalling or " +
                    "clearing the cache spends a fresh request each time.",
                action = cause.retryAfterSeconds?.let { seconds ->
                    "Wait about ${humaniseSeconds(seconds)}, then try again."
                } ?: "Wait a few minutes, then try again.",
                technical = "",
                canRetry = true,
                retryAfterSeconds = cause.retryAfterSeconds,
            )

            401, 403 -> AppError(
                kind = ErrorKind.Rejected,
                source = source,
                summary = "$source refused the request.",
                detail = "The API rejected this app rather than failing. For these two " +
                    "key-less APIs that usually means a policy change — an endpoint that " +
                    "now needs a token, or a blocked user agent.",
                action = null,
                technical = "",
                canRetry = false,
            )

            404 -> AppError(
                kind = ErrorKind.NotFound,
                source = source,
                summary = "$source no longer has that endpoint.",
                detail = "The address this app asks for returned nothing. APIs retire " +
                    "versioned endpoints, so this normally needs an app update rather " +
                    "than a retry.",
                action = null,
                technical = "",
                canRetry = false,
            )

            in 500..599 -> AppError(
                kind = ErrorKind.ServerError,
                source = source,
                summary = "$source is having trouble.",
                detail = "The API accepted the request and then failed to answer it, so " +
                    "the problem is at their end. These usually clear within minutes.",
                action = "Try again shortly.",
                technical = "",
                canRetry = true,
            )

            else -> AppError(
                kind = ErrorKind.Unknown,
                source = source,
                summary = "$source rejected the request (HTTP ${cause.code}).",
                detail = "The API answered with a status this app does not handle " +
                    "specifically. The technical details below are worth including in a " +
                    "bug report.",
                action = "Try again.",
                technical = "",
                canRetry = true,
            )
        }

        private fun buildTechnical(
            cause: Throwable,
            source: String,
            url: String,
            attemptedAt: Instant,
            error: AppError,
        ): String = buildString {
            appendLine("source:    $source")
            appendLine("kind:      ${error.kind.name}")
            if (cause is HttpFailure) {
                appendLine("status:    HTTP ${cause.code}")
                cause.retryAfterSeconds?.let { appendLine("retry-after: ${it}s") }
            }
            appendLine("request:   GET $url")
            appendLine("attempted: ${TechnicalTimeFormat.format(attemptedAt)}")
            appendLine("exception: ${cause.javaClass.name}")
            cause.message?.let { appendLine("message:   $it") }
            cause.cause?.let { appendLine("caused by: ${it.javaClass.name}: ${it.message}") }
        }.trimEnd()

        /**
         * Collapses several failures into one panel when they are the same failure.
         *
         * Both feeds load together, so being offline produces two identical errors. Showing
         * two identical panels tells the user nothing extra and makes the screen look
         * broken, so same-kind errors merge and name both sources instead.
         */
        fun merge(errors: List<AppError>): List<AppError> {
            if (errors.size < 2) return errors
            return errors
                .groupBy { it.kind }
                .map { (_, group) ->
                    if (group.size == 1) {
                        group.single()
                    } else {
                        val sources = group.map { it.source }.distinct()
                        val first = group.first()
                        first.copy(
                            source = sources.joinToString(" and "),
                            summary = first.summary.replace(first.source, sources.first()),
                            technical = group.joinToString("\n\n") { it.technical },
                        )
                    }
                }
        }

        private fun humaniseSeconds(seconds: Int): String = when {
            seconds < 90 -> "a minute"
            seconds < 3600 -> "${(seconds + 59) / 60} minutes"
            else -> "an hour"
        }

        private val TechnicalTimeFormat: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}

/** Coarse failure category, used to pick an icon, a colour and whether to offer a retry. */
enum class ErrorKind {
    Offline,
    Timeout,
    RateLimited,
    ServerError,
    NotFound,
    Rejected,
    BadData,
    Unknown,
}

/**
 * Thrown for any non-2xx response, carrying the facts [AppError] needs to phrase itself.
 */
class HttpFailure(
    val code: Int,
    val source: String,
    /** Parsed from the `Retry-After` response header when the server sent one. */
    val retryAfterSeconds: Int? = null,
) : IOException("HTTP $code from $source")

/** A load that failed, already described for the user. Thrown by `SpaceRepository`. */
class LoadFailure(val error: AppError, cause: Throwable) : Exception(error.summary, cause)
