package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniZipClient
import com.nuvio.app.core.anilist.KitsuClient
import com.nuvio.app.core.anilist.fanartUrl
import com.nuvio.app.core.anilist.kitsuId
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.watchprogress.ContinueWatchingItem
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-episode stills for the AniList Continue Watching cards.
 *
 * Luna resolves these the same way (`app/(tabs)/index.tsx`): one lookup per card for the episode's
 * own still, then the show's landscape backdrop when the episode has none, applied asynchronously so
 * the card renders immediately and upgrades in place. This adds Kitsu ahead of that chain, since its
 * ids are per-cour and it covers recent seasons well.
 *
 * Both underlying clients cache, and the details page hits the same entries, so a card usually costs
 * nothing after the first visit to that show.
 */
object AniListEpisodeThumbnails {
    private val _thumbnails = MutableStateFlow<Map<String, String>>(emptyMap())
    private val hydrated = atomic(false)

    /** Episode still keyed by [ContinueWatchingItem.videoId]. */
    val thumbnails: StateFlow<Map<String, String>> = _thumbnails.asStateFlow()

    /**
     * Restores stills resolved in earlier sessions. Without this every card costs an ani.zip lookup
     * plus a Kitsu episode list on each cold start, so the Continue Watching row sat on series art
     * for as long as those took to answer. Runs once per process.
     */
    suspend fun ensureLoaded() {
        if (!hydrated.compareAndSet(expect = false, update = true)) return
        val cached = withContext(Dispatchers.Default) { AniListEpisodeThumbnailCache.load() } ?: return
        // Anything resolved in this process is newer, so it wins.
        _thumbnails.value = cached + _thumbnails.value
    }

    fun hasUnresolved(items: List<ContinueWatchingItem>): Boolean {
        val resolved = _thumbnails.value
        return items.any { item -> isAniListId(item.parentMetaId) && item.videoId !in resolved }
    }

    suspend fun resolve(items: List<ContinueWatchingItem>) {
        val pending = items.filter { item ->
            isAniListId(item.parentMetaId) && item.videoId !in _thumbnails.value
        }
        if (pending.isEmpty()) return

        val resolved = coroutineScope {
            pending.map { item -> async { item.videoId to thumbnailFor(item) } }.awaitAll()
        }
        // Misses are stored as a blank so a title with no art is not retried on every recomposition.
        _thumbnails.value = _thumbnails.value + resolved.toMap()
        withContext(Dispatchers.Default) { AniListEpisodeThumbnailCache.save(_thumbnails.value) }
    }

    fun clear() {
        hydrated.value = false
        _thumbnails.value = emptyMap()
        AniListEpisodeThumbnailCache.clear()
    }

    private suspend fun thumbnailFor(item: ContinueWatchingItem): String {
        val mediaId = parseAniListMediaId(item.parentMetaId) ?: return ""
        val episodeNumber = item.episodeNumber ?: return ""

        return try {
            val aniZip = AniZipClient.mappings(mediaId)
            val kitsuThumbnail = aniZip?.kitsuId()?.let { kitsuId ->
                KitsuClient.episodes(kitsuId = kitsuId, expectedCount = episodeNumber)[episodeNumber]
                    ?.thumbnailUrl
            }
            kitsuThumbnail
                ?: aniZip?.episodes?.get(episodeNumber.toString())?.image?.takeIf { it.isNotBlank() }
                // No episode still: the show's TVDB landscape backdrop still beats a portrait cover
                // on a wide card. This is the same fallback Luna applies.
                ?: aniZip?.fanartUrl()
                ?: ""
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            ""
        }
    }
}

/**
 * The resolved stills, on disk. Blank entries — the "this title has no art" markers — are left out,
 * so a still that only appears once the episode has aired is still picked up on a later launch.
 */
private object AniListEpisodeThumbnailCache {
    private const val VERSION = 1
    private const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 400

    private val log = Logger.withTag("AniListThumbnailCache")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Map<String, String>? {
        val payload = AniListCacheStorage
            .loadPayload(ANILIST_EPISODE_THUMBNAIL_CACHE_KEY)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val stored = runCatching { json.decodeFromString<StoredThumbnails>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable episode thumbnail cache" } }
            .getOrNull()
            ?: return null

        if (stored.version != VERSION) return null
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        if (stored.storedAtEpochMs <= 0L || now - stored.storedAtEpochMs > MAX_AGE_MS) return null

        return stored.thumbnails.takeIf { it.isNotEmpty() }
    }

    fun save(thumbnails: Map<String, String>) {
        val resolved = thumbnails.entries
            .asSequence()
            .filter { (_, url) -> url.isNotBlank() }
            .take(MAX_ENTRIES)
            .associate { (videoId, url) -> videoId to url }
        if (resolved.isEmpty()) return

        val payload = json.encodeToString(
            StoredThumbnails(
                version = VERSION,
                storedAtEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
                thumbnails = resolved,
            ),
        )
        runCatching { AniListCacheStorage.savePayload(ANILIST_EPISODE_THUMBNAIL_CACHE_KEY, payload) }
            .onFailure { error -> log.w(error) { "Failed to persist episode thumbnail cache" } }
    }

    fun clear() {
        runCatching { AniListCacheStorage.removePayload(ANILIST_EPISODE_THUMBNAIL_CACHE_KEY) }
    }
}

@Serializable
private data class StoredThumbnails(
    val version: Int = 0,
    val storedAtEpochMs: Long = 0L,
    val thumbnails: Map<String, String> = emptyMap(),
)

/** Applies any resolved still to the matching card, leaving other items untouched. */
fun List<ContinueWatchingItem>.withAniListEpisodeThumbnails(
    thumbnails: Map<String, String>,
): List<ContinueWatchingItem> {
    if (thumbnails.isEmpty()) return this
    return map { item ->
        val thumbnail = thumbnails[item.videoId]?.takeIf { it.isNotBlank() }
        if (thumbnail == null || !isAniListId(item.parentMetaId)) {
            item
        } else {
            item.copy(imageUrl = thumbnail, episodeThumbnail = thumbnail)
        }
    }
}
