package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.core.mal.MalAnimeListEntry
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object MalListCache {
    private const val VERSION = 1
    private const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 2_000
    private val log = Logger.withTag("MalListCache")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    fun load(profileId: Int, accountId: Int): MalListsSnapshot? {
        val payload = MalCacheStorage.load(profileId)?.takeIf(String::isNotBlank) ?: return null
        return decodePayload(payload, accountId, EpisodeReleaseDatePlatform.nowEpochMs())
    }

    fun save(profileId: Int, snapshot: MalListsSnapshot, accountId: Int) {
        runCatching {
            MalCacheStorage.save(
                profileId,
                encodePayload(snapshot, accountId, EpisodeReleaseDatePlatform.nowEpochMs()),
            )
        }.onFailure { log.w(it) { "Failed to persist MAL list cache" } }
    }

    fun clear(profileId: Int) = MalCacheStorage.remove(profileId)

    internal fun decodePayload(payload: String, accountId: Int, nowEpochMs: Long): MalListsSnapshot? {
        val stored = runCatching { json.decodeFromString<StoredMalLists>(payload) }
            .onFailure { log.w(it) { "Discarding unreadable MAL list cache" } }
            .getOrNull() ?: return null
        if (stored.version != VERSION || stored.accountId != accountId) return null
        if (stored.storedAtEpochMs <= 0L || nowEpochMs < stored.storedAtEpochMs ||
            nowEpochMs - stored.storedAtEpochMs > MAX_AGE_MS
        ) return null
        if (stored.entries.isEmpty()) return null
        return MalListsSnapshot(stored.entries, stored.storedAtEpochMs)
    }

    internal fun encodePayload(snapshot: MalListsSnapshot, accountId: Int, nowEpochMs: Long): String =
        json.encodeToString(
            StoredMalLists(
                version = VERSION,
                storedAtEpochMs = snapshot.loadedAtEpochMs ?: nowEpochMs,
                accountId = accountId,
                entries = snapshot.entries
                    .sortedByDescending { it.listStatus.updatedAt.orEmpty() }
                    .take(MAX_ENTRIES),
            ),
        )
}

@Serializable
private data class StoredMalLists(
    val version: Int = 0,
    val storedAtEpochMs: Long = 0,
    val accountId: Int = 0,
    val entries: List<MalAnimeListEntry> = emptyList(),
)
