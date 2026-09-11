package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * One `api.ani.zip/mappings` document: external ids, series artwork, and per-episode metadata
 * (TVDB-sourced titles, overviews, stills, air dates and runtimes) keyed by episode number.
 *
 * Regular episodes use numeric keys ("1", "2", …); specials and OVAs use `S`-prefixed keys, so
 * walking `1..episodeCount` naturally skips them.
 */
@Serializable
data class AniZipResponse(
    val episodes: Map<String, AniZipEpisode> = emptyMap(),
    val episodeCount: Int? = null,
    val specialCount: Int? = null,
    val images: List<AniZipImage> = emptyList(),
    val mappings: JsonObject? = null,
)

@Serializable
data class AniZipEpisode(
    val tvdbShowId: Int? = null,
    val tvdbId: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val absoluteEpisodeNumber: Int? = null,
    /**
     * Values are nullable: ani.zip emits `"title": {"en": null}` for episodes that have an air date
     * but no title yet. A non-null `String` here fails the whole document's decode, which silently
     * costs that title its episode metadata *and* its artwork — observed on 8 of 8 hero items,
     * because currently-airing shows always have unaired episodes.
     */
    val title: Map<String, String?> = emptyMap(),
    val airDate: String? = null,
    /** ani.zip emits both spellings depending on the source record. */
    @SerialName("airdate") val airDateAlt: String? = null,
    val airDateUtc: String? = null,
    val runtime: Int? = null,
    val length: Int? = null,
    val overview: String? = null,
    val summary: String? = null,
    val image: String? = null,
    val episode: String? = null,
    /** Arrives as a quoted decimal, e.g. "8.19". */
    val rating: String? = null,
) {
    val displayTitle: String?
        get() = title["en"]?.takeIf { it.isNotBlank() }
            ?: title["x-jat"]?.takeIf { it.isNotBlank() }
            ?: title["ja"]?.takeIf { it.isNotBlank() }

    val overviewText: String?
        get() = overview?.takeIf { it.isNotBlank() } ?: summary?.takeIf { it.isNotBlank() }

    val runtimeMinutes: Int?
        get() = (runtime ?: length)?.takeIf { it > 0 }

    val releasedDate: String?
        get() = airDateUtc?.takeIf { it.isNotBlank() }
            ?: airDate?.takeIf { it.isNotBlank() }
            ?: airDateAlt?.takeIf { it.isNotBlank() }

    val ratingValue: Double?
        get() = rating?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }
}

@Serializable
data class AniZipImage(
    /** Banner, Poster, Fanart or Clearlogo. */
    val coverType: String? = null,
    val url: String? = null,
)

/** ani.zip mixes quoted and unquoted ids (`"1429"` vs `267440`), so read the primitive's content. */
private fun JsonObject?.idString(key: String): String? =
    (this?.get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != "null" }

fun AniZipResponse.tmdbId(): String? = mappings.idString("themoviedb_id")

fun AniZipResponse.tvdbId(): String? = mappings.idString("thetvdb_id")

fun AniZipResponse.imdbId(): String? = mappings.idString("imdb_id")

fun AniZipResponse.malId(): String? = mappings.idString("mal_id")

fun AniZipResponse.kitsuId(): String? = mappings.idString("kitsu_id")

/** ani.zip's `type`, e.g. "TV" or "MOVIE". */
fun AniZipResponse.mappedType(): String? = mappings.idString("type")

fun AniZipResponse.artworkUrl(coverType: String): String? =
    images.firstOrNull { it.coverType.equals(coverType, ignoreCase = true) }
        ?.url
        ?.takeIf { it.isNotBlank() }

/** TVDB clear logo — transparent title art, which is what the hero and details header want. */
fun AniZipResponse.clearLogoUrl(): String? = artworkUrl("Clearlogo")

fun AniZipResponse.fanartUrl(): String? = artworkUrl("Fanart")

/**
 * Which TVDB/TMDB season this AniList entry actually is.
 *
 * AniList gives every season its own entry while TMDB and TVDB model a multi-season anime as one
 * show, so `themoviedb_id` is identical for Re:ZERO season 1 and season 4. This is the only signal
 * that says which season an entry maps to — without it, anything asked of TMDB comes back as
 * season 1's data. Taken as the most common value across the regular episodes, ignoring specials.
 */
fun AniZipResponse.mappedSeasonNumber(): Int? =
    episodes
        .filterKeys { key -> key.toIntOrNull() != null }
        .values
        .mapNotNull { it.seasonNumber }
        .takeIf { it.isNotEmpty() }
        ?.groupingBy { it }
        ?.eachCount()
        ?.maxByOrNull { (_, count) -> count }
        ?.key

object AniZipClient {
    private const val ENDPOINT = "https://api.ani.zip/mappings"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val CACHE_MAX_ENTRIES = 48

    private val log = Logger.withTag("AniZipClient")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<String, CachedMappings>()

    /** Returns null rather than throwing — every caller treats ani.zip as best-effort enrichment. */
    suspend fun mappings(anilistId: Int, forceRefresh: Boolean = false): AniZipResponse? =
        mappingsByQuery("anilist_id", anilistId, forceRefresh)

    /** Reverse mapping used by the MAL fallback to retain canonical `anilist:` ids. */
    suspend fun mappingsByMalId(malId: Int, forceRefresh: Boolean = false): AniZipResponse? =
        mappingsByQuery("mal_id", malId, forceRefresh)

    /** Reverse mapping for Kitsu-sourced rows, so their items share ids with the rest of the app. */
    suspend fun mappingsByKitsuId(kitsuId: Int, forceRefresh: Boolean = false): AniZipResponse? =
        mappingsByQuery("kitsu_id", kitsuId, forceRefresh)

    private suspend fun mappingsByQuery(
        key: String,
        id: Int,
        forceRefresh: Boolean,
    ): AniZipResponse? {
        if (id <= 0) return null
        val cacheKey = "$key:$id"

        if (!forceRefresh) {
            cacheMutex.withLock {
                cache[cacheKey]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }
            }?.let { return it.response }

            // Disk fallback, so a cold start does not re-fetch mappings it already had. Promoted
            // into memory so repeat hits in this process skip the decode.
            AniZipDiskCache.get(cacheKey, nowMs())?.let { stored ->
                cacheMutex.withLock {
                    cache.remove(cacheKey)
                    cache[cacheKey] = CachedMappings(stored, nowMs())
                    while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
                }
                return stored
            }
        }

        return runCatching {
            val payload = httpGetTextWithHeaders(
                url = "$ENDPOINT?$key=$id",
                headers = mapOf("User-Agent" to "Anivio", "Accept" to "application/json"),
            )
            json.decodeFromString<AniZipResponse>(payload)
        }.onSuccess { response ->
            cacheMutex.withLock {
                cache.remove(cacheKey)
                cache[cacheKey] = CachedMappings(response, nowMs())
                while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
            }
            AniZipDiskCache.put(cacheKey, response, nowMs())
        }.onFailure { error ->
            log.w(error) { "ani.zip mappings failed for $key=$id" }
        }.getOrNull()
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedMappings(val response: AniZipResponse, val storedAtMs: Long)
}
