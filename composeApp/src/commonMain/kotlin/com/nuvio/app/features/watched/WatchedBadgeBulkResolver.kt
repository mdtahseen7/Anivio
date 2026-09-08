package com.nuvio.app.features.watched

import co.touchlab.kermit.Logger
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private const val BADGE_RESOLUTION_CONCURRENCY = 2

private val log = Logger.withTag("WatchedBadgeBulk")

suspend fun resolveWatchedBadgesBulk(
    watchedItems: List<WatchedItem>,
    progressEntries: List<WatchProgressEntry>,
) {
    val touchedSeriesIds = buildSet {
        watchedItems.forEach { item ->
            if (item.type.isSeriesLikeWatchedType() && item.season != null && item.episode != null) {
                add(item.id)
            }
        }
        progressEntries.forEach { entry ->
            if (entry.parentMetaType.isSeriesLikeWatchedType() && entry.isEpisode && entry.isEffectivelyCompleted) {
                add(entry.parentMetaId)
            }
        }
        WatchedRepository.baseFullyWatchedSeriesKeys().mapNotNullTo(this, ::extractContentIdFromWatchedKey)
    }
    if (touchedSeriesIds.isEmpty()) return

    val todayIsoDate = CurrentDateProvider.todayIsoDate()
    // Use the full watchedKeys from UI state, which already folds in the extra keys the active
    // tracking provider reports on top of its watched items.
    val watchedKeys = WatchedRepository.uiState.value.watchedKeys

    log.i { "Bulk badge resolution starting: ${touchedSeriesIds.size} series candidates" }

    withContext(Dispatchers.Default) {
        val semaphore = Semaphore(BADGE_RESOLUTION_CONCURRENCY)
        val resolvedIds = mutableSetOf<String>()
        val resolvedStates = linkedMapOf<String, Boolean>()

        for (contentId in touchedSeriesIds) {
            semaphore.withPermit {
                val meta = try {
                    MetaDetailsRepository.fetch(type = "series", id = contentId, cacheResult = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                }
                if (meta != null) {
                    val isFullyWatched = WatchedRepository.calculateFullyWatchedSeriesState(
                        meta = meta,
                        todayIsoDate = todayIsoDate,
                        isEpisodeWatched = { episode ->
                            watchedItemKeys(meta.type, meta.id, episode.season, episode.episode)
                                .any(watchedKeys::contains)
                        },
                        isEpisodeCompleted = { episode ->
                            val playbackId = meta.episodePlaybackId(episode)
                            progressEntries.any { entry ->
                                entry.videoId == playbackId && entry.isEffectivelyCompleted
                            }
                        },
                    )
                    resolvedStates[watchedItemKey(meta.type, meta.id)] = isFullyWatched
                    resolvedIds.add(contentId)
                }
            }
            yield()
        }

        WatchedRepository.updateFullyWatchedSeriesStates(resolvedStates)
        log.i { "Bulk badge resolution complete: resolved ${resolvedIds.size}/${touchedSeriesIds.size}" }

        // No connected source aliases a title under more than one content id — local keys and
        // AniList media ids are both one per title — so any expansion still on disk is stale.
        WatchedRepository.setExpandedFullyWatchedSeriesKeys(emptySet())
    }
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

private fun String.isSeriesLikeWatchedType(): Boolean =
    trim().lowercase() in setOf("series", "show", "tv", "tvshow", "anime")
