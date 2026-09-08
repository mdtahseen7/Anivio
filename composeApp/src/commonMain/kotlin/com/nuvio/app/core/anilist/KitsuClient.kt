package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One episode as Kitsu describes it. */
data class KitsuEpisode(
    val number: Int,
    val title: String? = null,
    val synopsis: String? = null,
    val thumbnailUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null,
)

/**
 * Kitsu episode metadata, reached through the `kitsu_id` that `api.ani.zip` already resolves.
 *
 * Kitsu's episode thumbnails are per-season (its ids are per-cour like AniList's, so no season
 * collision) and cover recent shows well — but not universally: Re:ZERO season 1 has none while
 * season 4 has them for every episode. Callers should treat this as the preferred source and keep
 * ani.zip's TVDB screencaps as the fallback.
 */
object KitsuClient {
    private const val ENDPOINT = "https://kitsu.io/api/edge/anime"

    /** Kitsu rejects anything above 20 with `Limit exceeds maximum page size of 20`. */
    private const val PAGE_SIZE = 20

    /**
     * Caps the request fan-out. A 26-episode season costs two requests; without this a 1000-episode
     * show like One Piece would cost fifty. Episodes past this keep their ani.zip thumbnails.
     */
    private const val MAX_PAGES = 5

    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val CACHE_MAX_ENTRIES = 32

    private val log = Logger.withTag("KitsuClient")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<String, CachedEpisodes>()

    /** Episodes keyed by episode number. Empty on any failure — every caller has a fallback. */
    suspend fun episodes(kitsuId: String, expectedCount: Int? = null): Map<Int, KitsuEpisode> {
        if (kitsuId.isBlank()) return emptyMap()

        cacheMutex.withLock {
            cache[kitsuId]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }
        }?.let { return it.episodes }

        val collected = mutableMapOf<Int, KitsuEpisode>()
        val pageBudget = expectedCount
            ?.takeIf { it > 0 }
            ?.let { count -> ((count + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtMost(MAX_PAGES) }
            ?: MAX_PAGES

        runCatching {
            for (page in 0 until pageBudget) {
                val url = "$ENDPOINT/$kitsuId/episodes" +
                    "?page%5Blimit%5D=$PAGE_SIZE&page%5Boffset%5D=${page * PAGE_SIZE}&sort=number"
                val payload = httpGetTextWithHeaders(
                    url = url,
                    headers = mapOf(
                        // Kitsu is a JSON:API service and needs its own media type.
                        "Accept" to "application/vnd.api+json",
                        "User-Agent" to "Anivio",
                    ),
                )
                val response = json.decodeFromString<KitsuEpisodesResponse>(payload)
                response.data.forEach { entry ->
                    val attributes = entry.attributes ?: return@forEach
                    val number = attributes.number ?: return@forEach
                    collected[number] = KitsuEpisode(
                        number = number,
                        title = attributes.canonicalTitle?.takeIf { it.isNotBlank() },
                        synopsis = attributes.synopsis?.takeIf { it.isNotBlank() }
                            ?: attributes.description?.takeIf { it.isNotBlank() },
                        thumbnailUrl = attributes.thumbnail?.original?.takeIf { it.isNotBlank() },
                        airDate = attributes.airdate?.takeIf { it.isNotBlank() },
                        runtimeMinutes = attributes.length?.takeIf { it > 0 },
                    )
                }
                if (response.data.size < PAGE_SIZE || response.links?.next == null) break
            }
        }.onFailure { error ->
            log.w(error) { "Kitsu episode lookup failed for anime/$kitsuId" }
        }

        val result = collected.toMap()
        cacheMutex.withLock {
            cache.remove(kitsuId)
            cache[kitsuId] = CachedEpisodes(result, nowMs())
            while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
        }
        return result
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedEpisodes(val episodes: Map<Int, KitsuEpisode>, val storedAtMs: Long)
}

@Serializable
private data class KitsuEpisodesResponse(
    val data: List<KitsuEpisodeEntry> = emptyList(),
    val links: KitsuLinks? = null,
)

@Serializable
private data class KitsuLinks(val next: String? = null)

@Serializable
private data class KitsuEpisodeEntry(val attributes: KitsuEpisodeAttributes? = null)

@Serializable
private data class KitsuEpisodeAttributes(
    val number: Int? = null,
    val canonicalTitle: String? = null,
    val synopsis: String? = null,
    val description: String? = null,
    val airdate: String? = null,
    /** Runtime in minutes. */
    val length: Int? = null,
    val thumbnail: KitsuImage? = null,
)

@Serializable
private data class KitsuImage(val original: String? = null)
