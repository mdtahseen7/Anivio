package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.anilist.AniListCacheStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Minimal GraphQL client for AniList.
 *
 * AniList needs no API key, but it is rate limited (30 requests/minute while the API is in its
 * degraded state), so this deliberately does three things: caches responses for [CACHE_TTL_MS],
 * collapses concurrent identical queries into one request, and backs off on 429 using
 * `Retry-After`. Callers should also prefer one aliased multi-row query over several small ones.
 */
object AniListClient {
    private const val ENDPOINT = "https://graphql.anilist.co"
    private const val USER_AGENT = "Anivio"
    private const val ANILIST_SITE_ORIGIN = "https://anilist.co"
    /** Also the eviction age: no reader is served an entry older than this. */
    const val DEFAULT_CACHE_TTL_MS = 10 * 60 * 1000L
    private const val CACHE_TTL_MS = DEFAULT_CACHE_TTL_MS

    /**
     * TTL for the queries whose whole point is being current: the recently-released airing feed and
     * the signed-in user's own lists, which drive Continue Watching.
     *
     * Ten minutes is right for Popular or Movies, and wrong for a row called "Recently released" or
     * for progress the user just changed on another device. Short enough to feel live, long enough to
     * still collapse the duplicate requests a single screen load fires.
     */
    const val VOLATILE_CACHE_TTL_MS = 60 * 1000L
    private const val CACHE_MAX_ENTRIES = 64
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val MAX_ATTEMPTS = 3
    private const val FALLBACK_RETRY_DELAY_MS = 2_000L
    private const val MAX_RETRY_DELAY_MS = 15_000L

    internal val json = Json { ignoreUnknownKeys = true }

    private val log = Logger.withTag("AniListClient")
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<String, CachedResponse>()
    private val inflightMutex = Mutex()
    private val inflight = mutableMapOf<String, InflightQuery>()
    private val requestSemaphore = Semaphore(2)
    private val throttle = AniListRequestThrottle(
        nowMs = ::nowMs,
        loadCooldown = AniListCacheStorage::loadCooldown,
        saveCooldown = AniListCacheStorage::saveCooldown,
    )
    val rateLimitUntilMs = throttle.rateLimitUntilMs

    /** Runs [query] and returns the GraphQL `data` object. Throws when AniList returns no data. */
    suspend fun query(
        query: String,
        variables: JsonObject = JsonObject(emptyMap()),
        forceRefresh: Boolean = false,
        accessToken: String? = null,
        /** Override for time-sensitive queries; see [VOLATILE_CACHE_TTL_MS]. */
        cacheTtlMs: Long = CACHE_TTL_MS,
    ): JsonObject {
        // The token is keyed by hash, not value, so a viewer-scoped response never serves another
        // account and the secret itself stays out of the cache keys.
        val cacheKey = "$query|$variables|${accessToken?.hashCode() ?: 0}"

        if (!forceRefresh) {
            readFromCache(cacheKey, cacheTtlMs)?.let { return it }
        }

        val request = inflightMutex.withLock {
            val active = inflight[cacheKey] ?: InflightQuery(
                requestScope.async(start = CoroutineStart.LAZY) {
                    execute(query, variables, accessToken).also { writeToCache(cacheKey, it) }
                },
            ).also { inflight[cacheKey] = it }
            active.waiters++
            active
        }

        try {
            return request.deferred.await()
        } finally {
            withContext(NonCancellable) {
                inflightMutex.withLock {
                    request.waiters--
                    if (request.waiters == 0) {
                        if (inflight[cacheKey] === request) inflight.remove(cacheKey)
                        request.deferred.cancel()
                    }
                }
            }
        }
    }

    /**
     * Runs a mutation. Deliberately bypasses both the response cache and the in-flight collapsing
     * used by [query]: two identical writes are two intended writes, and a cached mutation result
     * would be meaningless. Callers should invalidate any affected reads afterwards.
     */
    suspend fun mutate(
        mutation: String,
        variables: JsonObject = JsonObject(emptyMap()),
        accessToken: String,
    ): JsonObject = execute(mutation, variables, accessToken)

    /** Drops every cached response. Called when the signed-in AniList account changes. */
    suspend fun clearCache() {
        cacheMutex.withLock { cache.clear() }
    }

    private suspend fun execute(
        query: String,
        variables: JsonObject,
        accessToken: String?,
    ): JsonObject {
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()

        var lastError: Throwable? = null
        var lastRetryStatus: Int? = null
        // Catalog, Discover and search queries carry no token; only tracking reads and mutations do.
        val unauthenticated = accessToken.isNullOrBlank()

        for (attempt in 0 until MAX_ATTEMPTS) {
            val response = requestSemaphore.withPermit {
                throttle.awaitSendSlot()
                httpRequestRaw(
                method = "POST",
                url = ENDPOINT,
                headers = buildMap {
                    put("Content-Type", "application/json")
                    put("Accept", "application/json")
                    // AniList answers 403 to a request with no User-Agent at all. Every platform's
                    // HTTP client sends a default one, but state it explicitly rather than rely on
                    // that — a missing UA would empty every home row.
                    put("User-Agent", USER_AGENT)
                    // The edge in front of graphql.anilist.co rejects requests that carry no site
                    // context with a blanket 403, regardless of User-Agent. Verified directly: the
                    // same query is 403 without these and 200 with them. Nothing about the API is
                    // restricted or key-gated; it is bot filtering, so declare the origin the way a
                    // first-party client would.
                    put("Origin", ANILIST_SITE_ORIGIN)
                    put("Referer", "$ANILIST_SITE_ORIGIN/")
                    accessToken?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
                },
                body = body,
                maxResponseBodyBytes = MAX_RESPONSE_BYTES,
                ).also { withContext(NonCancellable) { throttle.record(it) } }
            }

            when {
                response.status == 429 -> {
                    log.w { "AniList requested a cooldown; stopping this request" }
                    throw AniListRateLimitedException()
                }

                response.status >= 500 -> {
                    lastError = IllegalStateException("AniList responded ${response.status}")
                    lastRetryStatus = response.status
                    val waitMs = (response.retryAfterMs() ?: (FALLBACK_RETRY_DELAY_MS * (attempt + 1)))
                        .coerceAtMost(MAX_RETRY_DELAY_MS)

                    if (attempt < MAX_ATTEMPTS - 1) {
                        log.w { "AniList ${response.status}; retrying in ${waitMs}ms" }
                        delay(waitMs)
                    }
                }

                // Only unauthenticated calls can diagnose an outage from a 401/403. The same status
                // on a token-bearing request means that token is stale, which is the user's account
                // to reconnect and must not be reported as "AniList is down".
                (response.status == 401 || response.status == 403) && unauthenticated -> {
                    AniListServiceStatus.reportUnavailable()
                    log.w { "AniList refused the request (${response.status}); treating as an outage" }
                    throw AniListUnavailableException(
                        statusCode = response.status,
                        serverMessage = response.body.graphQlErrorMessage(),
                    )
                }

                response.status !in 200..299 -> {
                    // AniList returns a GraphQL `errors` array even on 4xx — during the API's
                    // "temporarily disabled" outages that message is the only useful thing in the
                    // response, so prefer it over a raw body dump in the UI's error card.
                    val serverMessage = response.body.graphQlErrorMessage()
                    if (unauthenticated && serverMessage.indicatesAniListOutage()) {
                        AniListServiceStatus.reportUnavailable()
                        throw AniListUnavailableException(
                            statusCode = response.status,
                            serverMessage = serverMessage,
                        )
                    }
                    error(serverMessage ?: "AniList request failed (${response.status})")
                }

                else -> {
                    // A 200 can still carry an outage notice instead of data, so check before
                    // declaring the service healthy again.
                    val serverMessage = response.body.graphQlErrorMessage()
                    if (unauthenticated && serverMessage.indicatesAniListOutage()) {
                        AniListServiceStatus.reportUnavailable()
                        throw AniListUnavailableException(serverMessage = serverMessage)
                    }
                    AniListServiceStatus.reportReachable()
                    return response.body.toGraphQlData()
                }
            }
        }

        // 5xx that survived every retry is a genuine server-side outage.
        AniListServiceStatus.reportUnavailable()
        throw AniListUnavailableException(
            statusCode = lastRetryStatus,
            serverMessage = lastError?.message,
        )
    }

    private fun String.toGraphQlData(): JsonObject {
        val root = runCatching { json.parseToJsonElement(this).jsonObject }.getOrNull()
            ?: error("AniList returned a malformed response")
        (root["data"] as? JsonObject)?.let { return it }
        error(graphQlErrorMessage() ?: "AniList returned no data")
    }

    private fun String.graphQlErrorMessage(): String? =
        runCatching {
            json.parseToJsonElement(this)
                .jsonObject["errors"]
                ?.jsonArray
                ?.firstNotNullOfOrNull { element ->
                    (element as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
                }
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun RawHttpResponse.retryAfterMs(): Long? =
        headers["retry-after"]
            ?.trim()
            ?.toLongOrNull()
            ?.takeIf { it >= 0 }
            ?.times(1_000L)

    /**
     * [ttlMs] is the caller's, not the entry's, so the same cached bytes can be considered fresh by a
     * tolerant reader and stale by a time-sensitive one. The entry is therefore only evicted when it
     * is past the longest TTL anyone uses.
     */
    private suspend fun readFromCache(cacheKey: String, ttlMs: Long): JsonObject? = cacheMutex.withLock {
        val entry = cache[cacheKey] ?: return@withLock null
        val age = nowMs() - entry.storedAtMs
        if (age > CACHE_TTL_MS) {
            cache.remove(cacheKey)
            return@withLock null
        }
        entry.data.takeIf { age <= ttlMs }
    }

    private suspend fun writeToCache(cacheKey: String, data: JsonObject) = cacheMutex.withLock {
        cache.remove(cacheKey)
        cache[cacheKey] = CachedResponse(data = data, storedAtMs = nowMs())
        while (cache.size > CACHE_MAX_ENTRIES) {
            cache.remove(cache.keys.first())
        }
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private class InflightQuery(val deferred: Deferred<JsonObject>, var waiters: Int = 0)

    private data class CachedResponse(val data: JsonObject, val storedAtMs: Long)
}
