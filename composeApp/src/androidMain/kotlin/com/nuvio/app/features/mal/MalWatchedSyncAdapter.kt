package com.nuvio.app.features.mal

import com.nuvio.app.core.mal.MalAnimeListEntry
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingWatchedProvider
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object MalWatchedSyncAdapter : TrackingWatchedProvider {
    override val providerId = TrackingProviderId.MAL

    override suspend fun pull(profileId: Int, pageSize: Int): List<WatchedItem> {
        MalListRepository.refresh(forceRefresh = false)
        return MalListRepository.snapshot().entries.flatMap(MalAnimeListEntry::toWatchedItems)
    }

    override suspend fun pullFullyWatchedSeriesKeys(profileId: Int): Set<String> =
        MalListRepository.snapshot().entries.asSequence()
            .filter { it.listStatus.status.equals(MalListStatusValue.COMPLETED, true) }
            .mapTo(mutableSetOf()) { "$MAL_ID_PREFIX${it.node.id}" }

    override fun observeExtraWatchedKeys(profileId: Int): Flow<Set<String>> =
        MalListRepository.uiState.map { state ->
            state.snapshot.entries.asSequence()
                .filter { it.listStatus.status.equals(MalListStatusValue.COMPLETED, true) }
                .mapTo(mutableSetOf()) { "$MAL_ID_PREFIX${it.node.id}" }
        }

    override suspend fun push(profileId: Int, items: Collection<WatchedItem>) {
        val highest = mutableMapOf<Int, Int>()
        items.forEach { item ->
            val id = malAnimeIdOf(item.id) ?: return@forEach
            val episode = item.episode ?: return@forEach
            highest[id] = maxOf(highest[id] ?: 0, episode)
        }
        val snapshot = MalListRepository.snapshot().entries
        highest.forEach { (id, episode) ->
            val current = snapshot.firstOrNull { it.node.id == id }?.listStatus?.numEpisodesWatched ?: 0
            if (episode > current) MalMutations.setProgress(id, episode)
        }
        if (highest.isNotEmpty()) MalListRepository.refresh(forceRefresh = true)
    }

    override suspend fun delete(profileId: Int, items: Collection<WatchedItem>) {
        val lowest = mutableMapOf<Int, Int>()
        items.forEach { item ->
            val id = malAnimeIdOf(item.id) ?: return@forEach
            val episode = item.episode ?: return@forEach
            lowest[id] = minOf(lowest[id] ?: Int.MAX_VALUE, episode)
        }
        lowest.forEach { (id, episode) -> MalMutations.setProgress(id, (episode - 1).coerceAtLeast(0)) }
        if (lowest.isNotEmpty()) MalListRepository.refresh(forceRefresh = true)
    }
}

internal fun MalAnimeListEntry.toWatchedItems(): List<WatchedItem> {
    val completed = listStatus.status.equals(MalListStatusValue.COMPLETED, true)
    val watchedThrough = if (completed && node.numEpisodes > 0) node.numEpisodes
    else listStatus.numEpisodesWatched
    if (watchedThrough <= 0) return emptyList()
    val id = "$MAL_ID_PREFIX${node.id}"
    val title = node.alternativeTitles?.en?.takeIf(String::isNotBlank) ?: node.title
    val poster = node.mainPicture?.large?.takeIf(String::isNotBlank) ?: node.mainPicture?.medium
    val markedAt = malUpdatedAtEpochMs(listStatus.updatedAt)
    return (1..watchedThrough).map { episode ->
        WatchedItem(
            id = id,
            type = malContentType(),
            name = title,
            poster = poster,
            releaseInfo = node.startDate?.take(4),
            season = MAL_SEASON,
            episode = episode,
            videoId = buildPlaybackVideoId(id, MAL_SEASON, episode),
            trackingProviderId = TrackingProviderId.MAL.storageId,
            trackingProviderItemId = node.id.toString(),
            trackingSourceUrl = "https://myanimelist.net/anime/${node.id}",
            markedAtEpochMs = markedAt,
        )
    }
}
