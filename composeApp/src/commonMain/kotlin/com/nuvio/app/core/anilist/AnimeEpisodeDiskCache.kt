package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.anilist.ANILIST_KITSU_EPISODES_CACHE_KEY
import com.nuvio.app.features.anilist.ANILIST_TVDB_EPISODES_CACHE_KEY
import com.nuvio.app.features.anilist.ANILIST_TVDB_TOKEN_CACHE_KEY
import com.nuvio.app.features.anilist.AniListCacheStorage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Generic LRU disk cache for the per-show enrichment lookups.
 *
 * [AniZipDiskCache], [AniListMediaDetailDiskCache] and [TvdbArtworkDiskCache] each hand-rolled this
 * same load/evict/persist cycle. Two more were needed — TVDB and Kitsu episode lists, which were
 * memory-only and so re-fetched on every cold start — so the shape is factored out here rather than
 * copied a fourth and fifth time.
 */
internal class JsonLruDiskCache<T>(
    private val storageKey: String,
    private val valueSerializer: KSerializer<T>,
    private val version: Int,
    private val maxAgeMs: Long,
    private val maxEntries: Int,
    private val name: String,
) {
    @Serializable
    private data class Envelope(
        val version: Int = 0,
        val storedAt: Map<String, Long> = emptyMap(),
        /** Values kept as encoded strings so one envelope serializer covers every value type. */
        val values: Map<String, String> = emptyMap(),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val log = Logger.withTag("AnimeDiskCache")

    private var loaded = false
    private val storedAt = linkedMapOf<String, Long>()
    private val values = linkedMapOf<String, String>()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val payload = AniListCacheStorage.loadPayload(storageKey)?.takeIf { it.isNotBlank() } ?: return
        val envelope = runCatching { json.decodeFromString<Envelope>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable $name cache" } }
            .getOrNull() ?: return
        if (envelope.version != version) return
        // Only keys present in both maps are usable.
        envelope.values.forEach { (key, encoded) ->
            val at = envelope.storedAt[key] ?: return@forEach
            storedAt[key] = at
            values[key] = encoded
        }
    }

    fun get(key: String, nowMs: Long): T? {
        ensureLoaded()
        val at = storedAt[key] ?: return null
        if (nowMs - at > maxAgeMs) {
            storedAt.remove(key)
            values.remove(key)
            return null
        }
        val encoded = values[key] ?: return null
        return runCatching { json.decodeFromString(valueSerializer, encoded) }
            .onFailure { error -> log.w(error) { "Dropping undecodable $name entry $key" } }
            .getOrNull()
    }

    fun put(key: String, value: T, nowMs: Long) {
        ensureLoaded()
        val encoded = runCatching { json.encodeToString(valueSerializer, value) }
            .onFailure { error -> log.w(error) { "Failed to encode $name entry $key" } }
            .getOrNull() ?: return
        storedAt.remove(key)
        values.remove(key)
        storedAt[key] = nowMs
        values[key] = encoded
        // Insertion-ordered, so the oldest write leaves first.
        while (values.size > maxEntries) {
            val oldest = values.keys.firstOrNull() ?: break
            values.remove(oldest)
            storedAt.remove(oldest)
        }
        persist()
    }

    private fun persist() {
        runCatching {
            AniListCacheStorage.savePayload(
                storageKey,
                json.encodeToString(
                    Envelope(version = version, storedAt = storedAt, values = values),
                ),
            )
        }.onFailure { error -> log.w(error) { "Failed to persist $name cache" } }
    }
}

private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

/**
 * TVDB's whole-series English episode list, keyed by series id.
 *
 * A day rather than a month: the list grows while a show airs, and an episode missing its title is
 * exactly what this lookup exists to fix.
 */
internal val TvdbEpisodesDiskCache = JsonLruDiskCache(
    storageKey = ANILIST_TVDB_EPISODES_CACHE_KEY,
    valueSerializer = MapSerializer(Int.serializer(), TvdbEpisode.serializer()),
    version = 1,
    maxAgeMs = ONE_DAY_MS,
    maxEntries = 120,
    name = "TVDB episodes",
)

/** Kitsu episode stills and titles, keyed by Kitsu id. Costs up to five requests to rebuild. */
internal val KitsuEpisodesDiskCache = JsonLruDiskCache(
    storageKey = ANILIST_KITSU_EPISODES_CACHE_KEY,
    valueSerializer = MapSerializer(Int.serializer(), KitsuEpisode.serializer()),
    version = 1,
    maxAgeMs = ONE_DAY_MS,
    maxEntries = 120,
    name = "Kitsu episodes",
)

@Serializable
internal data class StoredTvdbToken(val token: String, val fetchedAtEpochMs: Long)

/**
 * The TVDB bearer token.
 *
 * TVDB issues 24-hour tokens but the client only held one in memory, so every cold start spent a
 * `POST /login` before it could ask for anything — a round trip on the critical path of the first
 * details screen opened after launch, every launch.
 */
internal val TvdbTokenDiskCache = JsonLruDiskCache(
    storageKey = ANILIST_TVDB_TOKEN_CACHE_KEY,
    valueSerializer = StoredTvdbToken.serializer(),
    version = 1,
    // Slightly under TVDB's own 24h expiry, so a token is never used in its final minutes.
    maxAgeMs = 23L * 60 * 60 * 1000,
    maxEntries = 1,
    name = "TVDB token",
)
