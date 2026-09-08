package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.anilist.displayTitle
import com.nuvio.app.features.tracking.TrackingProgressProvider
import com.nuvio.app.features.tracking.TrackingProgressSnapshot
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.watchprogress.WatchProgressEntry
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Marks rows that came from an AniList list rather than local playback. */
internal const val WatchProgressSourceAniList = "anilist_list"

/** AniList list statuses that mean the user is part-way through a title. */
private val IN_PROGRESS_STATUSES = setOf(AniListListStatus.CURRENT, AniListListStatus.REPEATING)

/**
 * Projects AniList list progress into the app's watch-progress model.
 *
 * The important mismatch: AniList stores *episodes completed*, with no position inside an episode.
 * So every row here is a completed-episode marker, and Continue Watching derives the next-up card
 * from it. That is why [providesCompleteMetadata] is true (AniList gives us title and artwork) while
 * durations and positions are always zero.
 */
object AniListTrackingProgressProvider : TrackingProgressProvider {
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST
    // Content-keyed for the same reason as the library provider: emitting on loading-flag and
    // timestamp churn would have the progress engine chasing its own refreshes.
    override val changes: Flow<Unit> = AniListListRepository.uiState
        .map { state -> state.snapshot.contentFingerprint() }
        .distinctUntilChanged()
        .map { }
    override val providesCompleteMetadata: Boolean = true

    // AniList's own COMPLETED status is the history projection, handled by the watched adapter.
    override val ownsCompletedHistoryProjection: Boolean = true

    override fun ensureLoaded() = Unit

    override fun onProfileChanged() = AniListListRepository.clearLocalState()

    override fun clearLocalState() = AniListListRepository.clearLocalState()

    override suspend fun refresh(force: Boolean, sourceChanged: Boolean) =
        AniListListRepository.refresh(forceRefresh = force || sourceChanged)

    override fun snapshot(): TrackingProgressSnapshot {
        val state = AniListListRepository.uiState.value
        return TrackingProgressSnapshot(
            entries = state.snapshot.anime
                .asSequence()
                .filter { entry -> entry.status?.uppercase() in IN_PROGRESS_STATUSES }
                .mapNotNull(AniListMediaListEntry::toWatchProgressEntry)
                .toList(),
            hasLoadedRemoteProgress = state.snapshot.loadedAtEpochMs != null,
            errorMessage = state.errorMessage,
        )
    }

    /**
     * Rewinds progress by one episode, which is the closest AniList equivalent of "remove this from
     * Continue Watching". Dropping to zero would also be defensible, but it would throw away the
     * user's whole position on the title.
     */
    override suspend fun removeProgress(entries: Collection<WatchProgressEntry>) {
        entries
            .mapNotNull { entry -> aniListMediaIdOf(entry.parentMetaId) }
            .distinct()
            .forEach { mediaId ->
                val watched = AniListListRepository.snapshot().anime
                    .firstOrNull { entry -> entry.media?.id == mediaId }
                    ?.progress
                    ?: return@forEach
                AniListMutations.setProgress(mediaId = mediaId, progress = (watched - 1).coerceAtLeast(0))
            }
        AniListListRepository.refresh(forceRefresh = true)
    }
}

private fun AniListMediaListEntry.toWatchProgressEntry(): WatchProgressEntry? {
    val media = media ?: return null
    val title = media.displayTitle() ?: return null

    val watched = (progress ?: 0).coerceAtLeast(0)
    // Nothing has been watched yet, so there is no completed episode to project.
    if (watched <= 0) return null

    val parentMetaId = "$ANILIST_ID_PREFIX${media.id}"
    val poster = media.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
        ?: media.coverImage?.large?.takeIf { it.isNotBlank() }

    return WatchProgressEntry(
        contentType = "series",
        parentMetaId = parentMetaId,
        parentMetaType = "series",
        videoId = buildPlaybackVideoId(
            parentMetaId = parentMetaId,
            seasonNumber = ANILIST_SEASON,
            episodeNumber = watched,
        ),
        title = title,
        poster = poster,
        background = media.bannerImage?.takeIf { it.isNotBlank() },
        seasonNumber = ANILIST_SEASON,
        episodeNumber = watched,
        // AniList tracks whole episodes only, so the last watched one is complete by definition.
        lastPositionMs = 0L,
        durationMs = 0L,
        lastUpdatedEpochMs = updatedAt?.takeIf { it > 0 }?.times(1_000L) ?: 0L,
        isCompleted = true,
        progressPercent = 100f,
        source = WatchProgressSourceAniList,
        trackingProviderId = TrackingProviderId.ANILIST.storageId,
        trackingProviderItemId = id?.toString(),
    )
}
