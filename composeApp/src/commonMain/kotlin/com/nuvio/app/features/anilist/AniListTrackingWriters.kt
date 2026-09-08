package com.nuvio.app.features.anilist

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

/** Playback past this point counts the episode as watched, matching the app's own threshold. */
private const val ANILIST_SCROBBLE_COMPLETION_PERCENT = 90.0

/** Moves titles between AniList statuses, and removes them from the account entirely. */
object AniListListWriter : TrackingListWriter {
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST

    override suspend fun moveToList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
        destination: TrackingListStatus,
    ): TrackingMutationResult {
        val wireStatus = AniListMutations.wireStatus(destination)
        var notFound = 0
        val resolutions = mutableListOf<TrackingMutationResolution>()

        items.forEach { item ->
            val mediaId = item.aniListMediaId()
            if (mediaId == null) {
                notFound++
                return@forEach
            }
            if (AniListMutations.setStatus(mediaId = mediaId, status = wireStatus)) {
                resolutions += TrackingMutationResolution(listStatus = destination)
            } else {
                notFound++
            }
        }

        if (resolutions.isNotEmpty()) AniListListRepository.refresh(forceRefresh = true)
        return TrackingMutationResult(
            attemptedCount = items.size,
            notFoundCount = notFound,
            resolutions = resolutions,
        )
    }

    override suspend fun removeFromList(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        var notFound = 0
        var removed = 0

        items.forEach { item ->
            val mediaId = item.aniListMediaId()
            // Deletion needs the entry id, which only exists once the title is on a list.
            val entryId = mediaId?.let { id ->
                AniListListRepository.snapshot().let { snapshot ->
                    (snapshot.anime + snapshot.manga)
                        .firstOrNull { entry -> entry.media?.id == id }
                        ?.id
                }
            }
            if (entryId == null) {
                notFound++
            } else if (AniListMutations.deleteEntry(entryId)) {
                removed++
            } else {
                notFound++
            }
        }

        if (removed > 0) AniListListRepository.refresh(forceRefresh = true)
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }
}

/**
 * Records watched episodes. AniList holds one progress counter per title, so marking an episode
 * watched means moving that counter — and only ever forward, so re-watching an early episode does
 * not discard later progress.
 */
object AniListHistoryWriter : TrackingHistoryWriter {
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST

    override suspend fun addToHistory(
        profileId: Int,
        items: Collection<TrackingHistoryItem>,
    ): TrackingMutationResult {
        val highestByMedia = mutableMapOf<Int, Int>()
        var notFound = 0

        items.forEach { item ->
            val mediaId = item.media.aniListMediaId()
            if (mediaId == null) {
                notFound++
                return@forEach
            }
            val episode = item.media.episode?.number
            if (episode == null) {
                // A movie, or a series marked without an episode number: completing it is the only
                // sensible reading.
                if (!AniListMutations.setStatus(mediaId, AniListListStatus.COMPLETED)) notFound++
            } else {
                highestByMedia[mediaId] = maxOf(highestByMedia[mediaId] ?: 0, episode)
            }
        }

        val snapshot = AniListListRepository.snapshot().anime
        highestByMedia.forEach { (mediaId, episode) ->
            val existing = snapshot.firstOrNull { entry -> entry.media?.id == mediaId }?.progress ?: 0
            if (episode > existing) AniListMutations.setProgress(mediaId, episode)
        }

        if (highestByMedia.isNotEmpty() || items.size > notFound) {
            AniListListRepository.refresh(forceRefresh = true)
        }
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }

    override suspend fun removeFromHistory(
        profileId: Int,
        items: Collection<TrackingMediaReference>,
    ): TrackingMutationResult {
        var notFound = 0
        var changed = false

        items.forEach { item ->
            val mediaId = item.aniListMediaId()
            if (mediaId == null) {
                notFound++
                return@forEach
            }
            val episode = item.episode?.number
            val target = if (episode != null) (episode - 1).coerceAtLeast(0) else 0
            if (AniListMutations.setProgress(mediaId, target)) changed = true else notFound++
        }

        if (changed) AniListListRepository.refresh(forceRefresh = true)
        return TrackingMutationResult(attemptedCount = items.size, notFoundCount = notFound)
    }
}

/**
 * AniList has no scrobble API, so this synthesises one: a stop past
 * [ANILIST_SCROBBLE_COMPLETION_PERCENT] advances the episode counter. Start and pause are ignored
 * because there is nowhere to put a partial position.
 */
object AniListScrobbler : TrackingScrobbler {
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST

    override suspend fun scrobble(
        profileId: Int,
        action: TrackingScrobbleAction,
        event: TrackingScrobbleEvent,
    ) {
        if (action != TrackingScrobbleAction.STOP) return
        if (event.progressPercent < ANILIST_SCROBBLE_COMPLETION_PERCENT) return

        val mediaId = event.media.aniListMediaId() ?: return
        val episode = event.media.episode?.number

        if (episode == null) {
            AniListMutations.setStatus(mediaId, AniListListStatus.COMPLETED)
        } else {
            val existing = AniListListRepository.snapshot().anime
                .firstOrNull { entry -> entry.media?.id == mediaId }
                ?.progress
                ?: 0
            if (episode <= existing) return
            AniListMutations.setProgress(mediaId, episode)
        }
        AniListListRepository.refresh(forceRefresh = true)
    }
}

/** Resolves an AniList media id from the explicit id when present, else from the catalog id. */
private fun TrackingMediaReference.aniListMediaId(): Int? =
    ids.anilist?.toInt()
        ?: catalog?.contentId?.let(::aniListMediaIdOf)
