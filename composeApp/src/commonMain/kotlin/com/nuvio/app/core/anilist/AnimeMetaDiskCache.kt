package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.anilist.ANILIST_ANIZIP_CACHE_KEY
import com.nuvio.app.features.anilist.ANILIST_MEDIA_DETAIL_CACHE_KEY
import com.nuvio.app.features.anilist.ANILIST_TVDB_ARTWORK_CACHE_KEY
import com.nuvio.app.features.anilist.AniListCacheStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Disk caches for the metadata that does not meaningfully change.
 *
 * The in-memory caches in [AniZipClient] and [TvdbClient] die with the process, so every cold start
 * re-fetched the same mappings and the same artwork. That matters more than it sounds: AniList allows
 * only 30 requests per minute, and each details screen fans out to ani.zip, Kitsu, TVDB and TMDB on
 * top of its AniList call. Persisting the two stable pieces removes them from the critical path
 * entirely on a revisit.
 *
 * Not cached here: the assembled `MetaDetails`, because it is not `@Serializable` and references
 * `StreamItem`. Caching the inputs gets most of the benefit without reshaping the model.
 */
private val cacheJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val log = Logger.withTag("AnimeMetaDiskCache")

// ================================================================================================
// ani.zip mappings
// ================================================================================================

@Serializable
private data class StoredAniZipEntry(
    val response: AniZipResponse,
    val storedAtEpochMs: Long,
)

@Serializable
private data class StoredAniZipCache(
    val version: Int = 0,
    val entries: Map<String, StoredAniZipEntry> = emptyMap(),
)

/**
 * ani.zip documents keyed the same way [AniZipClient] keys its memory cache, e.g. `anilist_id:1234`.
 *
 * Episode lists grow while a show airs, so the TTL is deliberately modest rather than permanent —
 * long enough to make navigation free, short enough that a currently-airing series picks up new
 * episodes the next day.
 */
internal object AniZipDiskCache {
    private const val VERSION = 1
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val MAX_ENTRIES = 200

    private var loaded = false
    private var entries: MutableMap<String, StoredAniZipEntry> = linkedMapOf()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = AniListCacheStorage.loadPayload(ANILIST_ANIZIP_CACHE_KEY)
            ?.takeIf { it.isNotBlank() } ?: return
        val stored = runCatching { cacheJson.decodeFromString<StoredAniZipCache>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable ani.zip cache" } }
            .getOrNull() ?: return
        if (stored.version != VERSION) return
        entries = LinkedHashMap(stored.entries)
    }

    fun get(cacheKey: String, nowMs: Long): AniZipResponse? {
        ensureLoaded()
        val entry = entries[cacheKey] ?: return null
        if (nowMs - entry.storedAtEpochMs > MAX_AGE_MS) {
            entries.remove(cacheKey)
            return null
        }
        return entry.response
    }

    fun put(cacheKey: String, response: AniZipResponse, nowMs: Long) {
        ensureLoaded()
        entries.remove(cacheKey)
        entries[cacheKey] = StoredAniZipEntry(response = response, storedAtEpochMs = nowMs)
        // Insertion-ordered, so the oldest write leaves first.
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        persist()
    }

    private fun persist() {
        runCatching {
            AniListCacheStorage.savePayload(
                ANILIST_ANIZIP_CACHE_KEY,
                cacheJson.encodeToString(StoredAniZipCache(version = VERSION, entries = entries)),
            )
        }.onFailure { error -> log.w(error) { "Failed to persist ani.zip cache" } }
    }
}

// ================================================================================================
// AniList media detail
// ================================================================================================

@Serializable
private data class StoredMediaDetailEntry(
    val detail: AniListMediaDetail,
    val storedAtEpochMs: Long,
)

@Serializable
private data class StoredMediaDetailCache(
    val version: Int = 0,
    val entries: Map<String, StoredMediaDetailEntry> = emptyMap(),
)

/**
 * The AniList `Media` document per media id — the single most valuable thing to keep, because it is
 * the one lookup that spends AniList's 30/minute budget.
 *
 * TTL is chosen from airing status, since volatility differs sharply:
 *
 *  - `FINISHED`/`CANCELLED`: title, art, genres, studios, staff, score and episode count are all
 *    settled. Cached for a month.
 *  - `RELEASING`/`HIATUS`/`NOT_YET_RELEASED`: the descriptive fields are just as static, but
 *    `nextAiringEpisode` moves weekly, the total `episodes` count gets corrected mid-run, and
 *    `averageScore` drifts. Cached for a day, which is well inside a weekly release cadence.
 *
 * The tradeoff on an airing show is a score that can be up to a day stale. Episode *content* does
 * not come from here — stills and titles are ani.zip/Kitsu/TVDB, cached separately.
 */
internal data class CachedMediaDetail(
    val detail: AniListMediaDetail,
    /**
     * False means "usable, but worth refreshing". The caller is expected to render it and revalidate
     * in the background rather than wait, which is the whole point: an airing show's synopsis, art,
     * genres and studios are as settled as a finished show's, and only the score and next-air date
     * lag.
     */
    val isFresh: Boolean,
)

internal object AniListMediaDetailDiskCache {
    // Bumped whenever the query's selection set grows: older entries decode fine but silently lack
    // the new field, so they must be discarded rather than served for another month.
    // 2: `recommendations`. 3: `airingSchedule`.
    private const val VERSION = 3
    private const val FINISHED_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val AIRING_MAX_AGE_MS = 24L * 60 * 60 * 1000

    /** Beyond this nothing is served without a network attempt, airing or not. */
    private const val HARD_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 200

    private var loaded = false
    private var entries: MutableMap<String, StoredMediaDetailEntry> = linkedMapOf()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = AniListCacheStorage.loadPayload(ANILIST_MEDIA_DETAIL_CACHE_KEY)
            ?.takeIf { it.isNotBlank() } ?: return
        val stored = runCatching { cacheJson.decodeFromString<StoredMediaDetailCache>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable media detail cache" } }
            .getOrNull() ?: return
        if (stored.version != VERSION) return
        entries = LinkedHashMap(stored.entries)
    }

    private fun maxAgeFor(status: String?): Long =
        when (status?.uppercase()) {
            "FINISHED", "CANCELLED" -> FINISHED_MAX_AGE_MS
            else -> AIRING_MAX_AGE_MS
        }

    /**
     * A usable cached copy, tagged with whether it still counts as fresh.
     *
     * Entries are kept on disk past their freshness window rather than dropped, so a throttled or
     * offline lookup can still render something ([getStale]).
     */
    fun peek(mediaId: Int, nowMs: Long): CachedMediaDetail? {
        ensureLoaded()
        val entry = entries[mediaId.toString()] ?: return null
        val age = nowMs - entry.storedAtEpochMs
        if (age > HARD_MAX_AGE_MS) return null
        return CachedMediaDetail(
            detail = entry.detail,
            isFresh = age <= maxAgeFor(entry.detail.status),
        )
    }

    /**
     * Any cached copy, however old.
     *
     * Only for the case where the live lookup could not be made at all — a 429 or an AniList
     * outage. A month-old synopsis renders; a failed request renders an error screen, and that was
     * the actual complaint.
     */
    fun getStale(mediaId: Int): AniListMediaDetail? {
        ensureLoaded()
        return entries[mediaId.toString()]?.detail
    }

    fun put(mediaId: Int, detail: AniListMediaDetail, nowMs: Long) {
        ensureLoaded()
        val key = mediaId.toString()
        entries.remove(key)
        entries[key] = StoredMediaDetailEntry(detail = detail, storedAtEpochMs = nowMs)
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        persist()
    }

    private fun persist() {
        runCatching {
            AniListCacheStorage.savePayload(
                ANILIST_MEDIA_DETAIL_CACHE_KEY,
                cacheJson.encodeToString(StoredMediaDetailCache(version = VERSION, entries = entries)),
            )
        }.onFailure { error -> log.w(error) { "Failed to persist media detail cache" } }
    }
}

// ================================================================================================
// TVDB series artwork
// ================================================================================================

@Serializable
private data class StoredArtworkEntry(
    val posterUrl: String? = null,
    val backgroundUrl: String? = null,
    val clearLogoUrl: String? = null,
    val storedAtEpochMs: Long = 0L,
)

@Serializable
private data class StoredArtworkCache(
    val version: Int = 0,
    val entries: Map<String, StoredArtworkEntry> = emptyMap(),
)

/**
 * Poster, background and clear logo per TVDB series id.
 *
 * A show's artwork is effectively immutable once published, so this gets a long TTL — this is the
 * cache that stops logos being re-fetched on every launch. [TvdbArtwork] itself is not serializable,
 * hence the flat mirror.
 */
internal object TvdbArtworkDiskCache {
    private const val VERSION = 1
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 300

    private var loaded = false
    private var entries: MutableMap<String, StoredArtworkEntry> = linkedMapOf()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = AniListCacheStorage.loadPayload(ANILIST_TVDB_ARTWORK_CACHE_KEY)
            ?.takeIf { it.isNotBlank() } ?: return
        val stored = runCatching { cacheJson.decodeFromString<StoredArtworkCache>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable TVDB artwork cache" } }
            .getOrNull() ?: return
        if (stored.version != VERSION) return
        entries = LinkedHashMap(stored.entries)
    }

    fun get(seriesId: String, nowMs: Long): TvdbArtwork? {
        ensureLoaded()
        val entry = entries[seriesId] ?: return null
        if (nowMs - entry.storedAtEpochMs > MAX_AGE_MS) {
            entries.remove(seriesId)
            return null
        }
        return TvdbArtwork(
            posterUrl = entry.posterUrl,
            backgroundUrl = entry.backgroundUrl,
            clearLogoUrl = entry.clearLogoUrl,
        )
    }

    fun put(seriesId: String, artwork: TvdbArtwork, nowMs: Long) {
        ensureLoaded()
        // An empty result usually means TVDB was unreachable or unconfigured. Caching that for a
        // month would hide artwork long after the cause was fixed.
        if (artwork.posterUrl == null && artwork.backgroundUrl == null && artwork.clearLogoUrl == null) {
            return
        }
        entries.remove(seriesId)
        entries[seriesId] = StoredArtworkEntry(
            posterUrl = artwork.posterUrl,
            backgroundUrl = artwork.backgroundUrl,
            clearLogoUrl = artwork.clearLogoUrl,
            storedAtEpochMs = nowMs,
        )
        while (entries.size > MAX_ENTRIES) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
        persist()
    }

    private fun persist() {
        runCatching {
            AniListCacheStorage.savePayload(
                ANILIST_TVDB_ARTWORK_CACHE_KEY,
                cacheJson.encodeToString(StoredArtworkCache(version = VERSION, entries = entries)),
            )
        }.onFailure { error -> log.w(error) { "Failed to persist TVDB artwork cache" } }
    }
}
