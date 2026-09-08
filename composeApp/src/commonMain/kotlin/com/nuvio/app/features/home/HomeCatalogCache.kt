package com.nuvio.app.features.home

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The home screen's rows and hero artwork, kept on disk between launches.
 *
 * Home is expensive to rebuild from scratch: the AniList rows cost a GraphQL request per refresh
 * against a 30/minute budget, and every hero item costs an ani.zip lookup plus TMDB / fanart.tv /
 * TVDB artwork calls. A cold start therefore used to sit on skeletons until the network answered.
 * With this, the previous session's rows paint on the first frame and the refresh happens behind
 * content the user is already reading -- stale-while-revalidate, not a replacement for refreshing.
 *
 * Only the *items* of a row are stored, keyed by [HomeCatalogDefinition.cacheKey]. Titles, order,
 * hero eligibility and catalog targets are rebuilt from the live definitions, so a row the user has
 * since renamed, reordered, disabled or uninstalled can never be resurrected by the cache.
 */
internal object HomeCatalogCache {
    private const val VERSION = 1

    /** Old enough that the rows are more likely wrong than useful; refetched from scratch instead. */
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    private const val MAX_ROWS = 48

    /** Home only ever previews `HOME_CATALOG_PREVIEW_FETCH_LIMIT` items per row. */
    private const val MAX_ITEMS_PER_ROW = 24

    /** The hero shows 8 at a time, but shuffles from the whole pool, so keep a healthy margin. */
    private const val MAX_HERO_ARTWORK_ENTRIES = 160

    private val log = Logger.withTag("HomeCatalogCache")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Blocking disk read -- call from a background coroutine. */
    fun load(): HomeCatalogCacheSnapshot? {
        val payload = HomeCatalogCacheStorage.loadPayload()?.takeIf { it.isNotBlank() } ?: return null
        return decodePayload(payload = payload, nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs())
    }

    /** Blocking disk write -- call from a background coroutine. */
    fun save(
        sections: Map<String, HomeCatalogSection>,
        heroArtwork: Map<String, MetaPreview>,
    ) {
        val payload = encodePayload(
            sections = sections,
            heroArtwork = heroArtwork,
            nowEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
        ) ?: return

        runCatching { HomeCatalogCacheStorage.savePayload(payload) }
            .onFailure { error -> log.w(error) { "Failed to persist home cache" } }
    }

    fun clear() {
        runCatching { HomeCatalogCacheStorage.clearPayload() }
    }

    /** Split from [load] so the version and age guards are testable without a storage backend. */
    internal fun decodePayload(payload: String, nowEpochMs: Long): HomeCatalogCacheSnapshot? {
        val stored = runCatching { json.decodeFromString<StoredHomeCatalogCache>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable home cache" } }
            .getOrNull()
            ?: return null

        if (stored.version != VERSION) return null
        if (stored.storedAtEpochMs <= 0L || nowEpochMs - stored.storedAtEpochMs > MAX_AGE_MS) return null

        return HomeCatalogCacheSnapshot(
            rows = stored.rows
                .filter { row -> row.cacheKey.isNotBlank() && row.items.isNotEmpty() }
                .associate { row ->
                    row.cacheKey to HomeCatalogCachedRow(
                        items = row.items,
                        availableItemCount = row.availableItemCount,
                        hasMore = row.hasMore,
                    )
                },
            heroArtwork = stored.heroArtwork.associate { entry -> entry.key to entry.item },
        )
    }

    /** Null when there is nothing worth storing. Split from [save] for the same reason. */
    internal fun encodePayload(
        sections: Map<String, HomeCatalogSection>,
        heroArtwork: Map<String, MetaPreview>,
        nowEpochMs: Long,
    ): String? {
        val rows = sections.entries
            .asSequence()
            .filter { (cacheKey, section) -> cacheKey.isNotBlank() && section.items.isNotEmpty() }
            .take(MAX_ROWS)
            .map { (cacheKey, section) ->
                StoredHomeCatalogRow(
                    cacheKey = cacheKey,
                    items = section.items.take(MAX_ITEMS_PER_ROW),
                    availableItemCount = section.availableItemCount,
                    hasMore = section.hasMore,
                )
            }
            .toList()

        if (rows.isEmpty() && heroArtwork.isEmpty()) return null

        return json.encodeToString(
            StoredHomeCatalogCache(
                version = VERSION,
                storedAtEpochMs = nowEpochMs,
                rows = rows,
                heroArtwork = heroArtwork.entries
                    .take(MAX_HERO_ARTWORK_ENTRIES)
                    .map { (key, item) -> StoredHomeHeroArtwork(key = key, item = item) },
            ),
        )
    }
}

internal data class HomeCatalogCachedRow(
    val items: List<MetaPreview>,
    val availableItemCount: Int,
    val hasMore: Boolean,
)

internal data class HomeCatalogCacheSnapshot(
    /** Keyed by [HomeCatalogDefinition.cacheKey]. */
    val rows: Map<String, HomeCatalogCachedRow>,
    /** Keyed by [MetaPreview.stableKey], as [com.nuvio.app.features.anilist.AniListHeroArtwork] keys it. */
    val heroArtwork: Map<String, MetaPreview>,
) {
    val isEmpty: Boolean get() = rows.isEmpty() && heroArtwork.isEmpty()
}

@Serializable
private data class StoredHomeCatalogCache(
    val version: Int = 0,
    val storedAtEpochMs: Long = 0L,
    val rows: List<StoredHomeCatalogRow> = emptyList(),
    val heroArtwork: List<StoredHomeHeroArtwork> = emptyList(),
)

@Serializable
private data class StoredHomeCatalogRow(
    val cacheKey: String = "",
    val items: List<MetaPreview> = emptyList(),
    val availableItemCount: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
private data class StoredHomeHeroArtwork(
    val key: String = "",
    val item: MetaPreview,
)
