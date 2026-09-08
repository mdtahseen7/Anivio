package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The signed-in user's AniList lists, kept on disk between launches.
 *
 * `MediaListCollection` is two requests and a viewer lookup before the library has a single row, and
 * on a cold start none of that has happened yet -- so the library sat on skeletons every time it was
 * opened. Hydrating from here means the rows are on the first frame and the refresh lands behind
 * them; a failed or rate-limited refresh now degrades to slightly stale rows instead of an empty
 * screen.
 *
 * Raw entries are stored rather than projected sections: [aniListLibraryProjection] stays the single
 * definition of what the rows are, and the same entry feeding several rows is stored once.
 */
internal object AniListListCache {
    private const val VERSION = 1

    /** Beyond this the lists are refetched from scratch rather than shown. */
    private const val MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000

    /** Bounds the payload for the rare user with a four-figure list; newest entries win. */
    private const val MAX_ENTRIES_PER_LIST = 800

    private val log = Logger.withTag("AniListListCache")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Blocking disk read -- call from a background coroutine. Returns null when there is nothing
     * usable, including when [accountId] does not match the account the payload was written for.
     */
    fun load(accountId: Int?): AniListListsSnapshot? {
        val payload = AniListCacheStorage.loadPayload(ANILIST_LIST_CACHE_KEY)?.takeIf { it.isNotBlank() } ?: return null
        return decodePayload(
            payload = payload,
            accountId = accountId,
            nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
        )
    }

    /** Blocking disk write -- call from a background coroutine. */
    fun save(snapshot: AniListListsSnapshot, accountId: Int?) {
        val payload = encodePayload(
            snapshot = snapshot,
            accountId = accountId,
            nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
        )

        runCatching { AniListCacheStorage.savePayload(ANILIST_LIST_CACHE_KEY, payload) }
            .onFailure { error -> log.w(error) { "Failed to persist AniList list cache" } }
    }

    /** Called when the AniList account is disconnected, so the next connection starts clean. */
    fun clear() {
        runCatching { AniListCacheStorage.removePayload(ANILIST_LIST_CACHE_KEY) }
    }

    /** Split from [load] so the version, account and age guards are testable without storage. */
    internal fun decodePayload(payload: String, accountId: Int?, nowEpochMs: Long): AniListListsSnapshot? {
        val stored = runCatching { json.decodeFromString<StoredAniListLists>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable AniList list cache" } }
            .getOrNull()
            ?: return null

        if (stored.version != VERSION) return null
        if (accountId != null && stored.accountId != null && stored.accountId != accountId) return null
        if (stored.storedAtEpochMs <= 0L || nowEpochMs - stored.storedAtEpochMs > MAX_AGE_MS) return null
        if (stored.anime.isEmpty() && stored.manga.isEmpty()) return null

        return AniListListsSnapshot(
            anime = stored.anime,
            manga = stored.manga,
            loadedAtEpochMs = stored.storedAtEpochMs,
        )
    }

    /** Split from [save] for the same reason. */
    internal fun encodePayload(
        snapshot: AniListListsSnapshot,
        accountId: Int?,
        nowEpochMs: Long,
    ): String = json.encodeToString(
        StoredAniListLists(
            version = VERSION,
            storedAtEpochMs = snapshot.loadedAtEpochMs ?: nowEpochMs,
            accountId = accountId,
            anime = snapshot.anime.newestFirst(),
            manga = snapshot.manga.newestFirst(),
        ),
    )

    private fun List<AniListMediaListEntry>.newestFirst(): List<AniListMediaListEntry> =
        sortedByDescending { entry -> entry.updatedAt ?: 0L }.take(MAX_ENTRIES_PER_LIST)
}

@Serializable
private data class StoredAniListLists(
    val version: Int = 0,
    val storedAtEpochMs: Long = 0L,
    /** Guards against a second account on the same profile reading the first one's lists. */
    val accountId: Int? = null,
    val anime: List<AniListMediaListEntry> = emptyList(),
    val manga: List<AniListMediaListEntry> = emptyList(),
)
