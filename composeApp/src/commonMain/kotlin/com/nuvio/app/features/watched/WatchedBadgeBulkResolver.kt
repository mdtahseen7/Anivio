package com.nuvio.app.features.watched

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.AppVisibilityState
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tracking.effectiveWatchProgressSource
import com.nuvio.app.features.tracking.providerId
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watching.domain.isSeriesLikeWatchingContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val BADGE_RESOLUTION_CONCURRENCY = 2
private const val AMBIGUOUS_MARKER = "__ambiguous__"

private val log = Logger.withTag("WatchedBadgeBulk")

suspend fun resolveWatchedBadgesBulk(
    watchedItems: List<WatchedItem>,
    progressEntries: List<WatchProgressEntry>,
    todayIsoDate: String = CurrentDateProvider.todayIsoDate(),
): Boolean = withContext(Dispatchers.Default) {
    val completedProgressVideoIds = progressEntries
        .asSequence()
        .filter { entry -> entry.isEffectivelyCompleted }
        .mapTo(mutableSetOf()) { entry -> entry.videoId }
    val touchedSeriesIds = buildSet {
        watchedItems.forEach { item ->
            if (
                item.type.isSeriesLikeWatchingContentType(includeAnime = true) &&
                item.season != null &&
                item.episode != null
            ) {
                add(item.id)
            }
        }
        progressEntries.forEach { entry ->
            if (
                entry.parentMetaType.isSeriesLikeWatchingContentType(includeAnime = true) &&
                entry.isEpisode &&
                entry.isEffectivelyCompleted
            ) {
                add(entry.parentMetaId)
            }
        }
        WatchedRepository.baseFullyWatchedSeriesKeys().mapNotNullTo(this, ::extractContentIdFromWatchedKey)
    }
    if (touchedSeriesIds.isEmpty()) return@withContext true

    // Use the full watchedKeys from UI state which includes extra keys from
    // provider alternate IDs, which the active tracking provider folds in on top of its watched items.
    val watchedKeys = WatchedRepository.uiState.value.watchedKeys

    // A badge is a visual affordance. Resolving the whole watch history for one while the app is not
    // on screen buys nothing, and when the screen has just gone off the radio is usually already
    // torn down, so every fetch in the walk fails in turn. Recomputed on the next foreground change.
    if (!AppVisibilityState.isForeground) {
        log.d { "Skipping bulk badge resolution while backgrounded" }
        return@withContext true
    }

    log.i { "Bulk badge resolution starting: ${touchedSeriesIds.size} series candidates" }

    val semaphore = Semaphore(BADGE_RESOLUTION_CONCURRENCY)
    val resolutions = coroutineScope {
        touchedSeriesIds.map { contentId ->
            async {
                semaphore.withPermit {
                    // A badge is decorative: it must never block Home. The metadata
                    // fetch can hang (a stalled enrichment call with no timeout),
                    // which left the app stuck on the splash screen forever.
                    // Cap each fetch at 20s (same as the details-page first-phase
                    // cap); on timeout the badge stays unresolved and Home loads.
                    val meta = try {
                        withTimeout(20_000) {
                            MetaDetailsRepository.fetch(type = "series", id = contentId, cacheResult = false)
                        }
                    } catch (_: TimeoutCancellationException) {
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }
                    if (meta == null || !meta.type.isSeriesLikeWatchingContentType(includeAnime = true) || meta.videos.isEmpty()) return@withPermit null
                    val isFullyWatched = WatchedRepository.calculateFullyWatchedSeriesState(
                        meta = meta,
                        todayIsoDate = todayIsoDate,
                        isEpisodeWatched = { episode ->
                            watchedItemKeys(meta.type, meta.id, episode.season, episode.episode)
                                .any(watchedKeys::contains)
                        },
                        isEpisodeCompleted = { episode ->
                            meta.episodePlaybackId(episode) in completedProgressVideoIds
                        },
                    )
                    watchedItemKey(meta.type, meta.id) to isFullyWatched
                }
            }
        }
    }.awaitAll()
    val resolvedStates = resolutions.filterNotNull().toMap(linkedMapOf())

    WatchedRepository.updateFullyWatchedSeriesStates(resolvedStates)
    log.i { "Bulk badge resolution complete: resolved ${resolutions.count { it != null }}/${touchedSeriesIds.size}" }

    expandFullyWatchedWithSiblings()
    resolutions.count { it != null } == touchedSeriesIds.size
}

fun expandFullyWatchedWithSiblings() {
    val siblingMap = getActiveProviderSiblingMap()
    if (siblingMap.isEmpty()) {
        WatchedRepository.setExpandedFullyWatchedSeriesKeys(emptySet())
        return
    }

    // Use base keys (without previous sibling expansion) to avoid feedback loops
    val baseKeys = WatchedRepository.baseFullyWatchedSeriesKeys()
    if (baseKeys.isEmpty()) {
        WatchedRepository.setExpandedFullyWatchedSeriesKeys(emptySet())
        return
    }

    // Compute only the sibling-derived keys (not including base keys themselves)
    val siblingKeys = buildSet {
        for (key in baseKeys) {
            val contentId = extractContentIdFromWatchedKey(key) ?: continue
            val siblings = siblingMap[contentId] ?: continue
            siblings.forEach { siblingId ->
                if (siblingId != contentId && !siblingId.startsWith(AMBIGUOUS_MARKER)) {
                    val siblingKey = rebuildWatchedKeyWithSiblingId(key, siblingId)
                    if (siblingKey != null) add(siblingKey)
                }
            }
        }
    }

    if (siblingKeys != WatchedRepository.currentExpandedSiblingKeys()) {
        log.i { "Sibling expansion: ${baseKeys.size} base keys -> +${siblingKeys.size} sibling keys" }
        WatchedRepository.setExpandedFullyWatchedSeriesKeys(siblingKeys)
    }
}

private fun getActiveProviderSiblingMap(): Map<String, Set<String>> {
    val source = TrackingSettingsRepository.uiState.value.watchProgressSource
    val effectiveSource = effectiveWatchProgressSource(
        requestedSource = source,
        isProviderAuthenticated = { providerId ->
            com.nuvio.app.features.tracking.TrackingProviderRegistry.isAuthenticated(providerId)
        },
    )
    return effectiveSource.providerId?.let {
        com.nuvio.app.features.tracking.TrackingProviderRegistry.progressProvider(it)?.showIdSiblings()
    }.orEmpty()
}

private fun extractContentIdFromWatchedKey(key: String): String? {
    // Format: "type:contentId:season:episode"
    // Split from the end to handle contentIds with colons (like "tmdb:123")
    val parts = key.split(':')
    if (parts.size < 4) return null
    // Last two parts are season and episode (-1:-1)
    // First part is type, everything in between is contentId
    val type = parts.first()
    val season = parts[parts.size - 2]
    val episode = parts.last()
    if (season.toIntOrNull() == null || episode.toIntOrNull() == null) return null
    val contentId = parts.subList(1, parts.size - 2).joinToString(":")
    return contentId.takeIf { it.isNotBlank() }
}

private fun rebuildWatchedKeyWithSiblingId(originalKey: String, siblingId: String): String? {
    val parts = originalKey.split(':')
    if (parts.size < 4) return null
    val type = parts.first()
    return watchedItemKey(type = type, id = siblingId)
}
