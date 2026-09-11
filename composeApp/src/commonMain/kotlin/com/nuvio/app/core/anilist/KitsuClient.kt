package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One episode as Kitsu describes it. */
@Serializable
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
    private const val TRENDING_ENDPOINT = "https://kitsu.io/api/edge/trending/anime"

    /** Catalog rows change far faster than episode metadata, so they get a much shorter TTL. */
    private const val CATALOG_CACHE_TTL_MS = 30 * 60 * 1000L

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
    private val catalogMutex = Mutex()
    private val catalogCache = linkedMapOf<String, CachedCatalog>()

    /** Episodes keyed by episode number. Empty on any failure — every caller has a fallback. */
    suspend fun episodes(kitsuId: String, expectedCount: Int? = null): Map<Int, KitsuEpisode> {
        if (kitsuId.isBlank()) return emptyMap()

        cacheMutex.withLock {
            cache[kitsuId]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }
        }?.let { return it.episodes }

        KitsuEpisodesDiskCache.get(kitsuId, nowMs())?.takeIf { it.isNotEmpty() }?.let { stored ->
            cacheMutex.withLock {
                cache.remove(kitsuId)
                cache[kitsuId] = CachedEpisodes(stored, nowMs())
                while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
            }
            return stored
        }

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
        // Worth persisting precisely because it is the most expensive lookup here: a long season is
        // several sequential pages, all of which a cold start used to repeat.
        if (result.isNotEmpty()) {
            KitsuEpisodesDiskCache.put(kitsuId, result, nowMs())
        }
        return result
    }

    /**
     * Kitsu's own trending list. Editorially better than a ranking sort — MAL's `airing` ranking is
     * effectively a popularity list, which is why trending and popular looked identical on it.
     *
     * Kitsu's trending endpoint takes no offset, so this is a single page by nature.
     */
    suspend fun trendingAnime(
        contentType: String,
        limit: Int = PAGE_SIZE,
        forceRefresh: Boolean = false,
    ): List<KitsuAnime> = animeList(
        cacheKey = "trending:$contentType:$limit",
        url = "$TRENDING_ENDPOINT?limit=${limit.coerceIn(1, PAGE_SIZE)}",
        subtypeFilter = contentType.kitsuSubtypeFilter(),
        forceRefresh = forceRefresh,
    )

    /**
     * Currently-airing titles, newest start date first. `filter[status]=current` is what makes this
     * "recently released" rather than "recently added to Kitsu".
     */
    suspend fun recentlyReleasedAnime(
        contentType: String,
        limit: Int = PAGE_SIZE,
        offset: Int = 0,
        forceRefresh: Boolean = false,
    ): List<KitsuAnime> {
        val bounded = limit.coerceIn(1, PAGE_SIZE)
        return animeList(
            cacheKey = "recent:$contentType:$bounded:$offset",
            url = buildString {
                append(ENDPOINT)
                append("?filter%5Bstatus%5D=current")
                append("&sort=-startDate")
                append("&page%5Blimit%5D=$bounded")
                append("&page%5Boffset%5D=${offset.coerceAtLeast(0)}")
                contentType.kitsuSubtype()?.let { append("&filter%5Bsubtype%5D=$it") }
            },
            // Already filtered server-side; re-filtering would drop nothing but costs nothing.
            subtypeFilter = contentType.kitsuSubtypeFilter(),
            forceRefresh = forceRefresh,
        )
    }

    private suspend fun animeList(
        cacheKey: String,
        url: String,
        subtypeFilter: (String?) -> Boolean,
        forceRefresh: Boolean,
    ): List<KitsuAnime> {
        if (!forceRefresh) {
            catalogMutex.withLock {
                catalogCache[cacheKey]?.takeIf { nowMs() - it.storedAtMs <= CATALOG_CACHE_TTL_MS }
            }?.let { return it.items }
        }

        val items = runCatching {
            val payload = httpGetTextWithHeaders(
                url = url,
                headers = mapOf(
                    "Accept" to "application/vnd.api+json",
                    "User-Agent" to "Anivio",
                ),
            )
            json.decodeFromString<KitsuAnimeResponse>(payload).data.mapNotNull { entry ->
                val id = entry.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val attributes = entry.attributes ?: return@mapNotNull null
                if (!subtypeFilter(attributes.subtype)) return@mapNotNull null
                val title = attributes.displayTitle ?: return@mapNotNull null
                KitsuAnime(
                    id = id,
                    title = title,
                    posterUrl = attributes.posterImage?.bestUrl,
                    synopsis = attributes.synopsis?.takeIf { it.isNotBlank() },
                    startDate = attributes.startDate?.takeIf { it.isNotBlank() },
                    // Kitsu scores out of 100 with one decimal; the app shows a /10 rating.
                    rating = attributes.averageRating
                        ?.toDoubleOrNull()
                        ?.takeIf { it > 0.0 }
                        ?.let { score -> (score / 10.0) },
                    userCount = attributes.userCount,
                    subtype = attributes.subtype,
                )
            }
        }.onFailure { error ->
            log.w(error) { "Kitsu catalog lookup failed ($cacheKey)" }
        }.getOrDefault(emptyList())

        if (items.isNotEmpty()) {
            catalogMutex.withLock {
                catalogCache.remove(cacheKey)
                catalogCache[cacheKey] = CachedCatalog(items, nowMs())
                while (catalogCache.size > CACHE_MAX_ENTRIES) catalogCache.remove(catalogCache.keys.first())
            }
        }
        return items
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedEpisodes(val episodes: Map<Int, KitsuEpisode>, val storedAtMs: Long)

    private data class CachedCatalog(val items: List<KitsuAnime>, val storedAtMs: Long)
}

/** A Kitsu anime as a catalog row needs it. */
data class KitsuAnime(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val synopsis: String? = null,
    val startDate: String? = null,
    val rating: Double? = null,
    val userCount: Int? = null,
    val subtype: String? = null,
)

/** Kitsu's `subtype` vocabulary: TV, movie, OVA, ONA, special, music. */
private fun String.kitsuSubtype(): String? =
    if (equals("movie", ignoreCase = true)) "movie" else null

private fun String.kitsuSubtypeFilter(): (String?) -> Boolean {
    val wantsMovie = equals("movie", ignoreCase = true)
    return { subtype ->
        val isMovie = subtype.equals("movie", ignoreCase = true)
        if (wantsMovie) isMovie else !isMovie
    }
}

@Serializable
private data class KitsuAnimeResponse(val data: List<KitsuAnimeEntry> = emptyList())

@Serializable
private data class KitsuAnimeEntry(
    val id: String? = null,
    val attributes: KitsuAnimeAttributes? = null,
)

@Serializable
private data class KitsuAnimeAttributes(
    val canonicalTitle: String? = null,
    val titles: Map<String, String?> = emptyMap(),
    val synopsis: String? = null,
    val startDate: String? = null,
    /** Arrives as a string, e.g. "82.14". */
    val averageRating: String? = null,
    val userCount: Int? = null,
    val subtype: String? = null,
    val posterImage: KitsuPoster? = null,
) {
    val displayTitle: String?
        get() = titles["en"]?.takeIf { !it.isNullOrBlank() }
            ?: titles["en_jp"]?.takeIf { !it.isNullOrBlank() }
            ?: canonicalTitle?.takeIf { it.isNotBlank() }
}

@Serializable
private data class KitsuPoster(
    val original: String? = null,
    val large: String? = null,
    val medium: String? = null,
) {
    /** `original` is the full-resolution grab, which is what the poster grid wants. */
    val bestUrl: String?
        get() = original?.takeIf { it.isNotBlank() }
            ?: large?.takeIf { it.isNotBlank() }
            ?: medium?.takeIf { it.isNotBlank() }
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
