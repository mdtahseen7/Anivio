package com.nuvio.app.core.anivio

import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Small unauthenticated client for Anivio's GraphQL metadata API. */
object AnivioClient {
    const val BASE_URL = "https://anivio-api.eu.cc"
    const val GRAPHQL_URL = "$BASE_URL/graphql"

    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private const val CACHE_MAX_ENTRIES = 32
    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<Int, CachedMedia>()
    private val inflightMutex = Mutex()
    private val inflight = mutableMapOf<Int, CompletableDeferred<AnivioMedia?>>()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Returns null when the AniList ID is not present in Anivio's catalog. */
    suspend fun mediaByAniListId(anilistId: Int, forceRefresh: Boolean = false): AnivioMedia? {
        require(anilistId > 0) { "AniList ID must be positive" }

        if (!forceRefresh) {
            val cached = cacheMutex.withLock {
                cache[anilistId]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }
            }
            if (cached != null) return cached.media
        }

        val deferred = inflightMutex.withLock {
            inflight[anilistId] ?: CompletableDeferred<AnivioMedia?>().also { created ->
                inflight[anilistId] = created
                requestScope.launch {
                    try {
                        val media = requestMedia(anilistId)
                        cacheMutex.withLock {
                            cache.remove(anilistId)
                            cache[anilistId] = CachedMedia(media, nowMs())
                            while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
                        }
                        created.complete(media)
                    } catch (error: Throwable) {
                        created.completeExceptionally(error)
                    } finally {
                        inflightMutex.withLock {
                            if (inflight[anilistId] === created) inflight.remove(anilistId)
                        }
                    }
                }
            }
        }
        return deferred.await()
    }

    /**
     * Best-effort variant for optional metadata enrichment.
     *
     * Transport failures, non-2xx responses, malformed payloads, and GraphQL failures become null so
     * callers can continue with the existing AniList/AniZip/TVDB path. Cancellation is deliberately
     * rethrown so leaving a screen still cancels the surrounding work normally.
     */
    suspend fun mediaByAniListIdOrNull(anilistId: Int, forceRefresh: Boolean = false): AnivioMedia? =
        try {
            mediaByAniListId(anilistId, forceRefresh)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            null
        }

    suspend fun clearCache() = cacheMutex.withLock { cache.clear() }

    private suspend fun requestMedia(anilistId: Int): AnivioMedia? {
        val response = httpRequestRaw(
            method = "POST",
            url = GRAPHQL_URL,
            headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json", "User-Agent" to "Anivio"),
            body = buildJsonObject {
                put("query", MEDIA_QUERY)
                put("variables", buildJsonObject { put("id", anilistId) })
            }.toString(),
            maxResponseBodyBytes = MAX_RESPONSE_BYTES,
        )
        if (response.status !in 200..299) error("Anivio API responded ${response.status}: ${response.body.take(300)}")

        val data = AniListClient.json.decodeFromString<GraphQlResponse>(response.body)
        if (data.errors.isNotEmpty() && data.data == null) {
            error(data.errors.firstOrNull()?.message ?: "Anivio GraphQL request failed")
        }
        return data.data?.media
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedMedia(val media: AnivioMedia?, val storedAtMs: Long)

    @Serializable private data class GraphQlResponse(
        val data: GraphQlData? = null,
        val errors: List<GraphQlError> = emptyList(),
    )

    @Serializable private data class GraphQlData(@SerialName("Media") val media: AnivioMedia? = null)
    @Serializable private data class GraphQlError(val message: String? = null)

    private const val MEDIA_QUERY = """
        query MediaByAniListId(${'$'}id: Int) {
          Media(id: ${'$'}id) {
            id idMal
            title { romaji english native }
            type format status description episodes duration season seasonYear
            averageScore popularity countryOfOrigin isAdult genres
            coverImage { extraLarge large medium color }
            bannerImage
            images { id url small medium large type source }
            artworks { id url large medium type source width height language isAdult }
          }
        }
    """
}
