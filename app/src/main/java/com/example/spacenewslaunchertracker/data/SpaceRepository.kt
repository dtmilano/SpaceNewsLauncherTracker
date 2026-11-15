package com.example.spacenewslaunchertracker.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Single source of launch and news data.
 *
 * Caching is deliberately two-layered, because Launch Library 2 only allows roughly 15
 * anonymous requests per hour:
 *  - an in-memory [Entry] per endpoint, so switching tabs, rotating the device, or ticking
 *    the live countdown never touches the network at all;
 *  - OkHttp's disk cache underneath it, so a cold start right after a warm one is also free.
 *
 * A larger `limit` is a superset of a smaller one, so one fetch at the largest requested
 * size serves every slider position below it.
 */
class SpaceRepository(cacheDir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val http = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "http"), HTTP_CACHE_BYTES))
        .callTimeout(20, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            // Neither API sends usable cache headers, so we impose our own freshness window.
            chain.proceed(chain.request())
                .newBuilder()
                .header("Cache-Control", "public, max-age=$FRESHNESS_SECONDS")
                .removeHeader("Pragma")
                .build()
        }
        .build()

    private class Entry<T>(val limit: Int, val value: T, val fetchedAtMillis: Long)

    @Volatile private var launches: Entry<List<UpcomingLaunch>>? = null

    @Volatile private var news: Entry<List<NewsArticle>>? = null

    /** Latest launches, from cache when a fetch of at least [limit] items is still fresh. */
    suspend fun upcomingLaunches(limit: Int): List<UpcomingLaunch> {
        launches?.takeIf { it.isUsableFor(limit) }?.let { return it.value.take(limit) }
        val url = "$LL2_BASE/2.3.0/launches/upcoming/" +
            "?limit=$FETCH_BATCH&mode=detailed&hide_recent_previous=true"
        val fetched = fetching(source = LL2_NAME, url = url) {
            val body = get(url, source = LL2_NAME)
            json.decodeFromString<LaunchListResponse>(body).results.map { it.toDomain() }
        }
        launches = Entry(FETCH_BATCH, fetched, System.currentTimeMillis())
        return fetched.take(limit)
    }

    /** Latest articles, from cache when a fetch of at least [limit] items is still fresh. */
    suspend fun recentNews(limit: Int): List<NewsArticle> {
        news?.takeIf { it.isUsableFor(limit) }?.let { return it.value.take(limit) }
        val url = "$SNAPI_BASE/v4/articles/?limit=$FETCH_BATCH&ordering=-published_at"
        val fetched = fetching(source = SNAPI_NAME, url = url) {
            val body = get(url, source = SNAPI_NAME)
            json.decodeFromString<ArticleListResponse>(body).results.map { it.toDomain() }
        }
        news = Entry(FETCH_BATCH, fetched, System.currentTimeMillis())
        return fetched.take(limit)
    }

    /** Drops every cached response so the next read goes back out to both APIs. */
    fun clearCaches() {
        launches = null
        news = null
        runCatching { http.cache?.evictAll() }
    }

    private fun get(url: String, source: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpFailure(
                    code = response.code,
                    source = source,
                    // `Retry-After` is either a delay in seconds or an HTTP date; only the
                    // former is worth turning into "wait about two minutes".
                    retryAfterSeconds = response.header("Retry-After")?.trim()?.toIntOrNull(),
                )
            }
            return response.body?.string().orEmpty()
        }
    }

    /**
     * Runs [block] on the IO dispatcher and turns any failure into a [LoadFailure] carrying
     * an [AppError] — the one place that knows both the API and the URL, which is what the
     * error's technical details need. Cancellation passes through untouched.
     */
    private suspend fun <T> fetching(source: String, url: String, block: () -> T): T {
        val attemptedAt = Instant.now()
        return try {
            withContext(Dispatchers.IO) { block() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (cause: Throwable) {
            throw LoadFailure(AppError.from(cause, source, url, attemptedAt), cause)
        }
    }

    private fun <T> Entry<T>.isUsableFor(requestedLimit: Int): Boolean =
        limit >= requestedLimit &&
            System.currentTimeMillis() - fetchedAtMillis < FRESHNESS_MILLIS

    private companion object {
        const val LL2_BASE = "https://ll.thespacedevs.com"
        const val SNAPI_BASE = "https://api.spaceflightnewsapi.net"
        const val LL2_NAME = "Launch Library 2"
        const val SNAPI_NAME = "Spaceflight News API"
        const val USER_AGENT = "SpaceNewsLauncherTracker/1.0 (Android sample app)"
        const val HTTP_CACHE_BYTES = 8L * 1024 * 1024

        /** One fetch at the slider maximum covers every smaller setting. */
        const val FETCH_BATCH = 30

        /** Well inside LL2's ~15 anonymous requests per hour, even with both tabs in use. */
        const val FRESHNESS_SECONDS = 15 * 60
        const val FRESHNESS_MILLIS = FRESHNESS_SECONDS * 1_000L
    }
}
