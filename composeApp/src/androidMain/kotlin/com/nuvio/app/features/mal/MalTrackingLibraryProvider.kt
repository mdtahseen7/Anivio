package com.nuvio.app.features.mal

import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import com.nuvio.app.features.tracking.TrackingLibraryProvider
import com.nuvio.app.features.tracking.TrackingLibrarySnapshot
import com.nuvio.app.features.tracking.TrackingLibraryTab
import com.nuvio.app.features.tracking.TrackingLibraryTabKind
import com.nuvio.app.features.tracking.TrackingListStatus
import com.nuvio.app.features.tracking.TrackingMembershipResolution
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingRefreshIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val MAL_SELECTION_GROUP = "mal:status"
private const val MAL_TAB_PREFIX = "mal:status:"

object MalTrackingLibraryProvider : TrackingLibraryProvider {
    override val providerId = TrackingProviderId.MAL
    override val changes: Flow<Unit> = MalListRepository.uiState
        .map { it.snapshot.contentFingerprint() }
        .distinctUntilChanged()
        .map { }
    override val connectionRefreshIntent = TrackingRefreshIntent.INVALIDATED

    override fun ensureLoaded() = Unit
    override fun onProfileChanged() = MalListRepository.clearLocalState()
    override fun clearLocalState() = MalListRepository.clearLocalState()

    override suspend fun refresh(intent: TrackingRefreshIntent) = MalListRepository.refresh(
        forceRefresh = intent != TrackingRefreshIntent.AUTOMATIC,
    )

    override fun snapshot(): TrackingLibrarySnapshot {
        val state = MalListRepository.uiState.value
        val sections = malLibraryProjection(state.snapshot)
        return TrackingLibrarySnapshot(
            items = sections.flatMap(LibrarySection::items).distinctBy(LibraryItem::id),
            sections = sections,
            tabs = statusTabs,
            hasLoaded = state.snapshot.loadedAtEpochMs != null,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
        )
    }

    override fun contains(contentId: String, contentType: String?): Boolean = findEntry(contentId) != null
    override fun find(contentId: String): LibraryItem? = findEntry(contentId)?.toLibraryItem()

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> {
        val status = findEntry(item.id)?.listStatus?.status?.lowercase()
        return malLibrarySections.associate { "$MAL_TAB_PREFIX${it.status}" to (it.status == status) }
    }

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> =
        if (currentMembership.any { it.value }) currentMembership.mapValues { false }
        else currentMembership.toMutableMap().apply {
            this["$MAL_TAB_PREFIX${MalListStatusValue.PLAN_TO_WATCH}"] = true
        }

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        val animeId = malAnimeIdOf(item.id) ?: return null
        val status = malLibrarySections.firstOrNull { desiredMembership["$MAL_TAB_PREFIX${it.status}"] == true }
        val succeeded = if (status == null) MalMutations.delete(animeId)
        else MalMutations.setStatus(animeId, status.status)
        if (succeeded) MalListRepository.refresh(forceRefresh = true)
        return null
    }

    private fun findEntry(contentId: String) = malAnimeIdOf(contentId)?.let { id ->
        MalListRepository.snapshot().entries.firstOrNull { it.node.id == id }
    }

    private val statusTabs = malLibrarySections.map {
        TrackingLibraryTab(
            key = "$MAL_TAB_PREFIX${it.status}",
            title = it.title,
            providerId = TrackingProviderId.MAL,
            kind = if (it.status == MalListStatusValue.PLAN_TO_WATCH) {
                TrackingLibraryTabKind.WATCHLIST
            } else TrackingLibraryTabKind.STATUS,
            selectionGroup = MAL_SELECTION_GROUP,
            supportedContentTypes = setOf("anime", "movie", "series"),
            semanticStatus = when (it.status) {
                MalListStatusValue.WATCHING -> TrackingListStatus.WATCHING
                MalListStatusValue.PLAN_TO_WATCH -> TrackingListStatus.PLAN_TO_WATCH
                MalListStatusValue.COMPLETED -> TrackingListStatus.COMPLETED
                MalListStatusValue.ON_HOLD -> TrackingListStatus.ON_HOLD
                MalListStatusValue.DROPPED -> TrackingListStatus.DROPPED
                else -> null
            },
        )
    }
}

internal fun MalListsSnapshot.contentFingerprint(): Int = entries.fold(1) { hash, entry ->
    var next = 31 * hash + entry.node.id
    next = 31 * next + entry.listStatus.status.hashCode()
    next = 31 * next + entry.listStatus.numEpisodesWatched
    next
}
