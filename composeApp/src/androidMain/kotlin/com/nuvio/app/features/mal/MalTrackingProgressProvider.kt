package com.nuvio.app.features.mal

import com.nuvio.app.core.mal.MalAnimeListEntry
import com.nuvio.app.features.tracking.TrackingProgressProvider
import com.nuvio.app.features.tracking.TrackingProgressSnapshot
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal const val WatchProgressSourceMal = "mal_list"

object MalTrackingProgressProvider : TrackingProgressProvider {
    override val providerId = TrackingProviderId.MAL
    override val changes: Flow<Unit> = MalListRepository.uiState
        .map { it.snapshot.contentFingerprint() }
        .distinctUntilChanged()
        .map { }
    override val providesCompleteMetadata = true
    override val ownsCompletedHistoryProjection = true

    override fun ensureLoaded() = Unit
    override fun onProfileChanged() = MalListRepository.clearLocalState()
    override fun clearLocalState() = MalListRepository.clearLocalState()
    override suspend fun refresh(force: Boolean, sourceChanged: Boolean) =
        MalListRepository.refresh(forceRefresh = force || sourceChanged)

    override fun snapshot(): TrackingProgressSnapshot {
        val state = MalListRepository.uiState.value
        return TrackingProgressSnapshot(
            entries = state.snapshot.entries.asSequence()
                .filter { it.listStatus.status.equals(MalListStatusValue.WATCHING, true) }
                .mapNotNull(MalAnimeListEntry::toWatchProgressEntry)
                .toList(),
            hasLoadedRemoteProgress = state.snapshot.loadedAtEpochMs != null,
            errorMessage = state.errorMessage,
        )
    }

    override suspend fun removeProgress(entries: Collection<WatchProgressEntry>) {
        entries.mapNotNull { malAnimeIdOf(it.parentMetaId) }.distinct().forEach { id ->
            val watched = MalListRepository.snapshot().entries
                .firstOrNull { it.node.id == id }?.listStatus?.numEpisodesWatched ?: return@forEach
            MalMutations.setProgress(id, (watched - 1).coerceAtLeast(0))
        }
        if (entries.isNotEmpty()) MalListRepository.refresh(forceRefresh = true)
    }
}

internal fun MalAnimeListEntry.toWatchProgressEntry(): WatchProgressEntry? {
    val watched = listStatus.numEpisodesWatched.coerceAtLeast(0)
    if (watched <= 0) return null
    val id = "$MAL_ID_PREFIX${node.id}"
    val title = node.alternativeTitles?.en?.takeIf(String::isNotBlank) ?: node.title
    val poster = node.mainPicture?.large?.takeIf(String::isNotBlank) ?: node.mainPicture?.medium
    return WatchProgressEntry(
        contentType = malContentType(),
        parentMetaId = id,
        parentMetaType = malContentType(),
        videoId = buildPlaybackVideoId(id, MAL_SEASON, watched),
        title = title,
        poster = poster,
        seasonNumber = MAL_SEASON,
        episodeNumber = watched,
        lastPositionMs = 0L,
        durationMs = 0L,
        lastUpdatedEpochMs = malUpdatedAtEpochMs(listStatus.updatedAt),
        isCompleted = true,
        progressPercent = 100f,
        source = WatchProgressSourceMal,
        trackingProviderId = TrackingProviderId.MAL.storageId,
        trackingProviderItemId = node.id.toString(),
        trackingSourceUrl = "https://myanimelist.net/anime/${node.id}",
    )
}
