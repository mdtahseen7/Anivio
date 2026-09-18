package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.RawHttpResponse
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.atomicfu.atomic
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

    /**
     * Caps how many requests are in the air at once.
     *
     * Opening a details screen while the home rows and hero artwork are still resolving used to fire
     * every query simultaneously. AniList answered most of them with 429, each one then retried on
     * its own 2s/4s backoff, and because the details pipeline is a serial chain those delays stacked
     * into the 10-20s stall. Queuing instead of bursting is what stops the 429s happening at all.
     */
    private const val MAX_CONCURRENT_REQUESTS = 3

    /** Minimum gap between two outgoing requests, smoothing bursts without feeling sluggish. */
    private const val MIN_REQUEST_SPACING_MS = 120L

    /**
     * Hard budget, measured from the live API: `X-RateLimit-Limit: 30` per 60s window.
     *
     * Held slightly under 30 because the window is the server's, not ours, and a retry spends budget
     * like any other request — which was the actual trap. Six requests each retrying three times is
     * eighteen against a thirty budget, so the retries were causing the rate limit they were
     * reacting to. Queuing under the cap avoids 429 entirely instead of recovering from it.
     */
    private const val BUDGET_WINDOW_MS = 60_000L
    private const val BUDGET_MAX_REQUESTS = 25

    internal val json = Json { ignoreUnknownKeys = true }

    private val log = Logger.withTag("AniListClient")
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<String, CachedResponse>()
    private val inflightMutex = Mutex()
    private val inflight = mutableMapOf<String, CompletableDeferred<JsonObject>>()

    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val throttleMutex = Mutex()
    private var nextEarliestSendAtMs = 0L

    /** Reserved send times inside the current window, oldest first. Guarded by [throttleMutex]. */
    private val reservedSendTimes = ArrayDeque<Long>()

    /**
     * Shared 429 backoff. Previously every request discovered the rate limit independently and kept
     * retrying into it; one request being throttled now pauses all of them, so the limit is respected
     * once rather than per-caller.
     */
    private val cooldownUntilMsRef = atomic(0L)
    private val _rateLimitUntilMs = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val rateLimitUntilMs: kotlinx.coroutines.flow.StateFlow<Long> = _rateLimitUntilMs

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

        val deferred = inflightMutex.withLock {
            inflight[cacheKey] ?: CompletableDeferred<JsonObject>().also { created ->
                inflight[cacheKey] = created
                requestScope.launch {
                    try {
                        val data = execute(query, variables, accessToken)
                        writeToCache(cacheKey, data)
                        created.complete(data)
                    } catch (error: Throwable) {
                        created.completeExceptionally(error)
                    } finally {
                        inflightMutex.withLock {
                            if (inflight[cacheKey] === created) inflight.remove(cacheKey)
                        }
                    }
                }
            }
        }

        return deferred.await()
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
        // Which status exhausted the retries, so 429 can be told apart from 5xx below.
        var lastRetryStatus: Int? = null
        // Catalog, Discover and search queries carry no token; only tracking reads and mutations do.
        val unauthenticated = accessToken.isNullOrBlank()

        // A `for` rather than `repeat`, because the 429 branch needs to abandon the remaining
        // attempts outright. `return@repeat` only ends one iteration, so it would have kept going.
        for (attempt in 0 until MAX_ATTEMPTS) {
            awaitSendSlot()
            val response = requestSemaphore.withPermit {
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
                )
            }

            when {
                response.status == 429 || response.status >= 500 -> {
                    lastError = IllegalStateException("AniList responded ${response.status}")
                    lastRetryStatus = response.status
                    val waitMs = (response.retryAfterMs() ?: (FALLBACK_RETRY_DELAY_MS * (attempt + 1)))
                        .coerceAtMost(MAX_RETRY_DELAY_MS)

                    if (response.status == 429) {
                        // Hold every other pending request back too, otherwise they walk into the
                        // same limit and each pays its own backoff.
                        applySharedCooldown(waitMs)
                        // Only one retry for a rate limit. A second attempt spends budget we do not
                        // have and pushes the window further out, which is how a brief throttle grew
                        // into a 15s stall.
                        if (attempt >= 1) {
                            log.w { "AniList 429 after ${attempt + 1} attempts; giving up on this call" }
                            break
                        }
                    }

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

        // A 429 is backpressure, not an outage. AniList allows ~30 requests/minute while degraded,
        // and one details screen plus the home hero can exhaust that in a burst — reporting it as an
        // outage flipped the entire app over to MAL for the whole probe window, which is why a
        // details page could come back to MAL-sourced rows. Fail this one call and stay on AniList.
        if (lastRetryStatus == 429) {
            log.w { "AniList rate limited after $MAX_ATTEMPTS attempts; not failing over" }
            throw AniListRateLimitedException(lastError?.message)
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

    /**
     * Blocks until this request is allowed out: first any shared 429 cooldown, then its own spacing
     * slot. The slot is reserved under the lock but waited on outside it, so callers queue rather
     * than serialising on the mutex itself.
     */
    private suspend fun awaitSendSlot() {
        while (true) {
            val cooldownUntil = cooldownUntilMsRef.value
            val remaining = cooldownUntil - nowMs()
            if (remaining <= 0L) break
            delay(remaining.coerceAtMost(MAX_RETRY_DELAY_MS))
        }

        val waitMs = throttleMutex.withLock {
            val now = nowMs()

            // Drop reservations that have aged out of the rolling window.
            while (reservedSendTimes.isNotEmpty() && now - reservedSendTimes.first() >= BUDGET_WINDOW_MS) {
                reservedSendTimes.removeFirst()
            }

            var sendAt = maxOf(now, nextEarliestSendAtMs)
            // At the cap, the next slot cannot open until the oldest reservation leaves the window.
            if (reservedSendTimes.size >= BUDGET_MAX_REQUESTS) {
                sendAt = maxOf(sendAt, reservedSendTimes.first() + BUDGET_WINDOW_MS)
            }

            // Reserving a future timestamp rather than "now" is what makes concurrent callers stack
            // correctly instead of all seeing the same free slot.
            nextEarliestSendAtMs = sendAt + MIN_REQUEST_SPACING_MS
            reservedSendTimes.addLast(sendAt)
            sendAt - now
        }
        if (waitMs > 0L) {
            log.d { "AniList budget reached; holding request for ${waitMs}ms" }
            delay(waitMs)
        }
    }

    /** Pauses every pending request, not just this one. */
    private fun applySharedCooldown(waitMs: Long) {
        val until = nowMs() + waitMs
        while (true) {
            val current = cooldownUntilMsRef.value
            if (current >= until) return
            if (cooldownUntilMsRef.compareAndSet(current, until)) {
                _rateLimitUntilMs.value = until
                return
            }
        }
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedResponse(val data: JsonObject, val storedAtMs: Long)
}
