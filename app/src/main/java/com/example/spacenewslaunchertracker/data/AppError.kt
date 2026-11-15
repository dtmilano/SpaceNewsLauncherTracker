package com.example.spacenewslaunchertracker.data

import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A failure already translated into something worth showing a person.
 *
 * Nothing in the UI layer ever sees a raw exception or a stack trace; [from] is the single
 * place where technical failures become sentences.
 */
data class AppError(
    val message: String,
    val isRateLimit: Boolean = false,
) {
    companion object {
        fun from(cause: Throwable): AppError = when (cause) {
            is HttpFailure -> fromHttp(cause)
            is UnknownHostException, is ConnectException ->
                AppError("No internet connection. Check your network and pull to retry.")
            is SocketTimeoutException ->
                AppError("The request timed out. The API may be slow right now — try again.")
            is SerializationException ->
                AppError("The API returned data this app couldn't read. Try refreshing.")
            is IOException ->
                AppError("Network problem while contacting the API. Try again in a moment.")
            else -> AppError("Something went wrong loading this data. Try refreshing.")
        }

        private fun fromHttp(cause: HttpFailure): AppError = when (cause.code) {
            429 -> AppError(
                "Rate limit hit (HTTP 429). ${cause.source} allows only a handful of " +
                    "anonymous requests per hour — please retry shortly. Cached data is " +
                    "reused between refreshes to stay under the limit.",
                isRateLimit = true,
            )
            in 500..599 -> AppError(
                "${cause.source} is having trouble (HTTP ${cause.code}). Try again shortly.",
            )
            404 -> AppError("${cause.source} could not find that data (HTTP 404).")
            else -> AppError("${cause.source} rejected the request (HTTP ${cause.code}).")
        }
    }
}

/** Thrown for any non-2xx response so that [AppError.from] can phrase it per status code. */
class HttpFailure(val code: Int, val source: String) :
    IOException("HTTP $code from $source")
