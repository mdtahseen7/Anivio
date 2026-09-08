package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.anilist.aniListContentType
import com.nuvio.app.core.anilist.displayTitle
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingWatchedProvider
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Projects AniList list state into watched history.
 *
 * Two things count as watched: every episode up to `progress` on an in-progress title, and a title
 * whose status is COMPLETED. AniList has no per-episode timestamps, so every item carries the
 * entry's `updatedAt` — good enough for ordering, not for "when did I watch episode 3".
 */
object AniListWatchedSyncAdapter : TrackingWatchedProvider {
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST

    override suspend fun pull(profileId: Int, pageSize: Int): List<WatchedItem> {
        AniListListRepository.refresh(forceRefresh = false)
        return AniListListRepository.snapshot().anime.flatMap(AniListMediaListEntry::toWatchedItems)
    }

    /**
     * Series the user has finished. Reported separately so Continue Watching can drop them without
     * needing a watched item per episode of a 1000-episode show.
     */
    override suspend fun pullFullyWatchedSeriesKeys(profileId: Int): Set<String> =
        AniListListRepository.snapshot().anime
            .asSequence()
            .filter { entry -> entry.status?.uppercase() == AniListListStatus.COMPLETED }
            .mapNotNull { entry -> entry.media?.id }
            .mapTo(mutableSetOf()) { mediaId -> "$ANILIST_ID_PREFIX$mediaId" }

    override fun observeExtraWatchedKeys(profileId: Int): Flow<Set<String>> =
        AniListListRepository.uiState.map { state ->
            state.snapshot.anime
                .asSequence()
                .filter { entry -> entry.status?.uppercase() == AniListListStatus.COMPLETED }
                .mapNotNull { entry -> entry.media?.id }
                .mapTo(mutableSetOf()) { mediaId -> "$ANILIST_ID_PREFIX$mediaId" }
        }

    /**
     * Pushes the highest watched episode per title. AniList stores a single progress counter, so a
     * batch of episodes collapses into one write per series, and the counter only ever moves
     * forward — a back-fill of an older episode must not undo a later one.
     */
    override suspend fun push(profileId: Int, items: Collection<WatchedItem>) {
        val highestByMedia = mutableMapOf<Int, Int>()
        items.forEach { item ->
            val mediaId = aniListMediaIdOf(item.id) ?: return@forEach
            val episode = item.episode ?: return@forEach
            highestByMedia[mediaId] = maxOf(highestByMedia[mediaId] ?: 0, episode)
        }
        if (highestByMedia.isEmpty()) return

        val snapshot = AniListListRepository.snapshot().anime
        highestByMedia.forEach { (mediaId, episode) ->
            val existing = snapshot.firstOrNull { entry -> entry.media?.id == mediaId }?.progress ?: 0
            if (episode > existing) {
                AniListMutations.setProgress(mediaId = mediaId, progress = episode)
            }
        }
        AniListListRepository.refresh(forceRefresh = true)
    }

    /**
     * Rolls progress back to just below the lowest episode being unmarked, since AniList cannot
     * represent "episode 5 unwatched but 6 watched".
     */
    override suspend fun delete(profileId: Int, items: Collection<WatchedItem>) {
        val lowestByMedia = mutableMapOf<Int, Int>()
        items.forEach { item ->
            val mediaId = aniListMediaIdOf(item.id) ?: return@forEach
            val episode = item.episode ?: return@forEach
            lowestByMedia[mediaId] = minOf(lowestByMedia[mediaId] ?: Int.MAX_VALUE, episode)
        }
        if (lowestByMedia.isEmpty()) return

        lowestByMedia.forEach { (mediaId, episode) ->
            AniListMutations.setProgress(mediaId = mediaId, progress = (episode - 1).coerceAtLeast(0))
        }
        AniListListRepository.refresh(forceRefresh = true)
    }
}

private fun AniListMediaListEntry.toWatchedItems(): List<WatchedItem> {
    val media = media ?: return emptyList()
    val title = media.displayTitle() ?: return emptyList()

    val isCompleted = status?.uppercase() == AniListListStatus.COMPLETED
    val totalEpisodes = media.episodes?.takeIf { it > 0 }
    val watchedThrough = when {
        // A completed title counts as watched all the way through, even when AniList never got a
        // per-episode progress value for it.
        isCompleted -> totalEpisodes ?: (progress ?: 0)
        else -> progress ?: 0
    }.coerceAtLeast(0)

    if (watchedThrough <= 0) return emptyList()

    val parentMetaId = "$ANILIST_ID_PREFIX${media.id}"
    val poster = media.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
        ?: media.coverImage?.large?.takeIf { it.isNotBlank() }
    val markedAt = updatedAt?.takeIf { it > 0 }?.times(1_000L) ?: 0L
    val contentType = media.aniListContentType()

    return (1..watchedThrough).map { episode ->
        WatchedItem(
            id = parentMetaId,
            type = contentType,
            name = title,
            poster = poster,
            releaseInfo = (media.seasonYear ?: media.startDate?.year)?.toString(),
            season = ANILIST_SEASON,
            episode = episode,
            videoId = buildPlaybackVideoId(
                parentMetaId = parentMetaId,
                seasonNumber = ANILIST_SEASON,
                episodeNumber = episode,
            ),
            trackingProviderId = TrackingProviderId.ANILIST.storageId,
            trackingProviderItemId = id?.toString(),
            markedAtEpochMs = markedAt,
        )
    }
}
