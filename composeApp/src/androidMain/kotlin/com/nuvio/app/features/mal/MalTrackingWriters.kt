package com.nuvio.app.features.mal

import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingHistoryWriter
import com.nuvio.app.features.tracking.TrackingListStatus
import com.nuvio.app.features.tracking.TrackingListWriter
import com.nuvio.app.features.tracking.TrackingMediaReference
import com.nuvio.app.features.tracking.TrackingMutationResolution
import com.nuvio.app.features.tracking.TrackingMutationResult
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingScrobbleAction
import com.nuvio.app.features.tracking.TrackingScrobbleEvent
import com.nuvio.app.features.tracking.TrackingScrobbler

object MalListWriter : TrackingListWriter {
    override val providerId = TrackingProviderId.MAL

    override suspend fun moveToList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
        destination: TrackingListStatus,
    ): TrackingMutationResult {
        var notFound = 0
        val resolutions = mutableListOf<TrackingMutationResolution>()
        items.forEach { media ->
            val id = media.resolveMalAnimeId()
            if (id == null || !MalMutations.setStatus(id, MalMutations.wireStatus(destination))) notFound++
            else resolutions += TrackingMutationResolution(listStatus = destination)
        }
        if (resolutions.isNotEmpty()) MalListRepository.refresh(forceRefresh = true)
        return TrackingMutationResult(items.size, notFound, resolutions)
    }

    override suspend fun removeFromList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        var notFound = 0
        var changed = false
        items.forEach { media ->
            val id = media.resolveMalAnimeId()
            if (id == null || !MalMutations.delete(id)) notFound++ else changed = true
        }
        if (changed) MalListRepository.refresh(forceRefresh = true)
        return TrackingMutationResult(items.size, notFound)
    }
}

object MalHistoryWriter : TrackingHistoryWriter {
    override val providerId = TrackingProviderId.MAL

    override suspend fun addToHistory(
        profileId: Int,
        items: Collection<TrackingHistoryItem>,
    ): TrackingMutationResult {
        val highest = mutableMapOf<Int, Int>()
        val complete = mutableSetOf<Int>()
        var notFound = 0
        items.forEach { history ->
            val id = history.media.resolveMalAnimeId()
            if (id == null) notFound++
            else history.media.episode?.number?.let { highest[id] = maxOf(highest[id] ?: 0, it) }
                ?: complete.add(id)
        }
        complete.forEach { if (!MalMutations.setStatus(it, MalListStatusValue.COMPLETED)) notFound++ }
        val snapshot = MalListRepository.snapshot().entries
        highest.forEach { (id, episode) ->
            val current = snapshot.firstOrNull { it.node.id == id }?.listStatus?.numEpisodesWatched ?: 0
            if (episode > current && !MalMutations.setProgress(id, episode)) notFound++
        }
        if (complete.isNotEmpty() || highest.isNotEmpty()) MalListRepository.refresh(forceRefresh = true)
        return TrackingMutationResult(items.size, notFound)
    }

    override suspend fun removeFromHistory(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        var notFound = 0
        var changed = false
        items.forEach { media ->
            val id = media.resolveMalAnimeId()
            val target = media.episode?.number?.minus(1)?.coerceAtLeast(0) ?: 0
            if (id == null || !MalMutations.setProgress(id, target)) notFound++ else changed = true
        }
        if (changed) MalListRepository.refresh(forceRefresh = true)
        return TrackingMutationResult(items.size, notFound)
    }
}

object MalScrobbler : TrackingScrobbler {
    override val providerId = TrackingProviderId.MAL

    override suspend fun scrobble(
        profileId: Int,
        action: TrackingScrobbleAction,
        event: TrackingScrobbleEvent,
    ) {
        if (action != TrackingScrobbleAction.STOP || event.progressPercent < 90.0) return
        val id = event.media.resolveMalAnimeId() ?: return
        val episode = event.media.episode?.number
        val changed = if (episode == null) MalMutations.setStatus(id, MalListStatusValue.COMPLETED)
        else {
            val current = MalListRepository.snapshot().entries
                .firstOrNull { it.node.id == id }?.listStatus?.numEpisodesWatched ?: 0
            episode > current && MalMutations.setProgress(id, episode)
        }
        if (changed) MalListRepository.refresh(forceRefresh = true)
    }
}
