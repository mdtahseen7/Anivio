package com.nuvio.app.features.profiles

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private val AvatarCatalogRefreshInterval = 15.minutes

@Serializable
private data class StoredAvatarCatalogPayload(val items: List<AvatarCatalogItem> = emptyList())

/**
 * Holds the profile avatar catalogue, now sourced from AniList characters grouped by show.
 *
 * The disk cache is what makes the picker paint immediately on a cold start; the network fetch then
 * runs behind it so a stale cache self-heals without ever showing an empty grid.
 */
object AvatarRepository {
    private val log = Logger.withTag("AvatarRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _avatars = MutableStateFlow<List<AvatarCatalogItem>>(emptyList())
    val avatars: StateFlow<List<AvatarCatalogItem>> = _avatars.asStateFlow()

    private var cacheHydrated = false
    private var fetchInFlight = false
    private var lastRefresh: TimeMark? = null

    suspend fun fetchAvatars() {
        hydrateFromCacheIfNeeded()
        if (_avatars.value.isNotEmpty()) return
        fetchCatalog(forceRefresh = false)
    }

    suspend fun refreshAvatars(force: Boolean = false) {
        hydrateFromCacheIfNeeded()
        if (force || isRefreshDue()) fetchCatalog(forceRefresh = force)
    }

    private fun isRefreshDue(): Boolean =
        lastRefresh?.let { it.elapsedNow() >= AvatarCatalogRefreshInterval } ?: true

    private fun hydrateFromCacheIfNeeded() {
        if (cacheHydrated) return
        cacheHydrated = true

        val payload = AvatarStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) return

        val stored = runCatching {
            json.decodeFromString<StoredAvatarCatalogPayload>(payload)
        }.getOrNull() ?: return

        _avatars.value = stored.items.orderedForPicker()
    }

    private suspend fun fetchCatalog(forceRefresh: Boolean) {
        if (fetchInFlight) return
        fetchInFlight = true
        try {
            val items = fetchAniListCharacterAvatars(forceRefresh = forceRefresh)
            // An empty result means AniList was unreachable, so keep whatever the cache gave us
            // rather than emptying a picker the user may already be looking at.
            if (items.isEmpty()) {
                log.w { "AniList returned no character avatars; keeping the cached catalogue" }
                return
            }
            lastRefresh = TimeSource.Monotonic.markNow()
            _avatars.value = items.orderedForPicker()
            AvatarStorage.savePayload(json.encodeToString(StoredAvatarCatalogPayload(items = items)))
        } finally {
            fetchInFlight = false
        }
    }

    /**
     * Restores the curated show order. Sorting by category name would look tidy but would scramble
     * the intended sequence, so the group key is ranked by its position in the AniList source list.
     */
    private fun List<AvatarCatalogItem>.orderedForPicker(): List<AvatarCatalogItem> {
        val animeOrder = aniListAvatarAnimeLabels.withIndex().associate { (index, label) -> label to index }
        return filter { it.isActive }
            .sortedWith(compareBy({ animeOrder[it.category] ?: Int.MAX_VALUE }, { it.sortOrder }))
    }
}
