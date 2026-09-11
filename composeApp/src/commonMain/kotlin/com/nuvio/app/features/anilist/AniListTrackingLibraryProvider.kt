package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
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
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_status_completed
import nuvio.composeapp.generated.resources.anilist_status_dropped
import nuvio.composeapp.generated.resources.anilist_status_paused
import nuvio.composeapp.generated.resources.anilist_status_planning
import nuvio.composeapp.generated.resources.anilist_status_watching
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Membership on AniList is a single status per title rather than a set of lists, so the tabs below
 * form one mutually exclusive group. [ANILIST_STATUS_SELECTION_GROUP] is what makes the library UI
 * clear the sibling status when a new one is picked.
 */
private const val ANILIST_STATUS_SELECTION_GROUP = "anilist:status"

private const val ANILIST_STATUS_TAB_PREFIX = "anilist:status:"

/** How long a lists snapshot is trusted before an automatic refresh will go to the network again. */
private const val ANILIST_LIBRARY_FRESHNESS_MS = 5 * 60 * 1000L

internal data class AniListStatusTab(
    val wireStatus: String,
    val titleResource: StringResource,
    val kind: TrackingLibraryTabKind,
    val semanticStatus: TrackingListStatus,
) {
    val key: String get() = "$ANILIST_STATUS_TAB_PREFIX$wireStatus"
}

internal val aniListStatusTabs = listOf(
    AniListStatusTab(
        wireStatus = AniListListStatus.CURRENT,
        titleResource = Res.string.anilist_status_watching,
        kind = TrackingLibraryTabKind.STATUS,
        semanticStatus = TrackingListStatus.WATCHING,
    ),
    // Planning is AniList's watchlist, so it is what a plain "add to library" tap targets.
    AniListStatusTab(
        wireStatus = AniListListStatus.PLANNING,
        titleResource = Res.string.anilist_status_planning,
        kind = TrackingLibraryTabKind.WATCHLIST,
        semanticStatus = TrackingListStatus.PLAN_TO_WATCH,
    ),
    AniListStatusTab(
        wireStatus = AniListListStatus.COMPLETED,
        titleResource = Res.string.anilist_status_completed,
        kind = TrackingLibraryTabKind.STATUS,
        semanticStatus = TrackingListStatus.COMPLETED,
    ),
    AniListStatusTab(
        wireStatus = AniListListStatus.PAUSED,
        titleResource = Res.string.anilist_status_paused,
        kind = TrackingLibraryTabKind.STATUS,
        semanticStatus = TrackingListStatus.ON_HOLD,
    ),
    AniListStatusTab(
        wireStatus = AniListListStatus.DROPPED,
        titleResource = Res.string.anilist_status_dropped,
        kind = TrackingLibraryTabKind.STATUS,
        semanticStatus = TrackingListStatus.DROPPED,
    ),
)

object AniListTrackingLibraryProvider : TrackingLibraryProvider {
    override val providerId: TrackingProviderId = TrackingProviderId.ANILIST
    // Keyed on list content, not the whole ui state: loading flags and the refresh timestamp change
    // on every fetch, and republishing on those alone feeds back into another refresh.
    override val changes: Flow<Unit> = AniListListRepository.uiState
        .map { state -> state.snapshot.contentFingerprint() }
        .distinctUntilChanged()
        .map { }

    // A fresh connection has no cached lists worth keeping, so always go to the network.
    override val connectionRefreshIntent: TrackingRefreshIntent = TrackingRefreshIntent.INVALIDATED

    override fun ensureLoaded() = Unit

    override fun onProfileChanged() = AniListListRepository.clearLocalState()

    override fun clearLocalState() = AniListListRepository.clearLocalState()

    override suspend fun refresh(intent: TrackingRefreshIntent) {
        when (intent) {
            // LibraryRepository.ensureLoaded() runs on nearly every library read and kicks off an
            // AUTOMATIC refresh each time, so this has to be freshness-gated. Ungated it becomes a
            // request storm that also starves user-initiated writes of the repository refresh lock.
            TrackingRefreshIntent.AUTOMATIC -> if (!isSnapshotFresh()) {
                AniListListRepository.refresh(forceRefresh = false)
            }

            TrackingRefreshIntent.USER_INITIATED,
            TrackingRefreshIntent.INVALIDATED,
            -> AniListListRepository.refresh(forceRefresh = true)
        }
    }

    private fun isSnapshotFresh(): Boolean {
        val loadedAt = AniListListRepository.snapshot().loadedAtEpochMs ?: return false
        return EpisodeReleaseDatePlatform.nowEpochMs() - loadedAt < ANILIST_LIBRARY_FRESHNESS_MS
    }

    /**
     * Resolved once: the titles are static, and [snapshot] is called often enough that doing a
     * blocking resource lookup per call would show up as jank.
     */
    private val statusTabs: List<TrackingLibraryTab> by lazy {
        aniListStatusTabs.map { tab ->
            TrackingLibraryTab(
                key = tab.key,
                title = runBlocking { getString(tab.titleResource) },
                providerId = TrackingProviderId.ANILIST,
                kind = tab.kind,
                selectionGroup = ANILIST_STATUS_SELECTION_GROUP,
                semanticStatus = tab.semanticStatus,
            )
        }
    }

    override fun snapshot(): TrackingLibrarySnapshot {
        val state = AniListListRepository.uiState.value
        val sections = aniListLibraryProjection(state.snapshot)
        return TrackingLibrarySnapshot(
            items = sections.flatMap(LibrarySection::items).distinctBy(LibraryItem::id),
            sections = sections,
            tabs = statusTabs,
            hasLoaded = state.snapshot.loadedAtEpochMs != null,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
        )
    }

    override fun contains(contentId: String, contentType: String?): Boolean =
        findEntry(contentId) != null

    override fun find(contentId: String): LibraryItem? =
        aniListLibraryProjection(AniListListRepository.snapshot())
            .asSequence()
            .flatMap { section -> section.items.asSequence() }
            .firstOrNull { item -> item.id == contentId }

    override suspend fun membership(item: LibraryItem): Map<String, Boolean> {
        val currentStatus = findEntry(item.id)?.status?.uppercase()
        val normalized = currentStatus?.let { status ->
            // REPEATING is shown as Watching, so it must select the Watching tab.
            if (status == AniListListStatus.REPEATING) AniListListStatus.CURRENT else status
        }
        return aniListStatusTabs.associate { tab -> tab.key to (tab.wireStatus == normalized) }
    }

    override fun toggledDefaultMembership(
        currentMembership: Map<String, Boolean>,
    ): Map<String, Boolean> {
        val planningKey = "$ANILIST_STATUS_TAB_PREFIX${AniListListStatus.PLANNING}"
        val alreadyOnAList = currentMembership.any { (_, selected) -> selected }
        return if (alreadyOnAList) {
            // Toggling off means leaving every status, which deletes the entry.
            currentMembership.mapValues { false }
        } else {
            currentMembership.toMutableMap().apply { this[planningKey] = true }
        }
    }

    override suspend fun applyMembership(
        profileId: Int,
        item: LibraryItem,
        desiredMembership: Map<String, Boolean>,
        destructiveRemovalConfirmed: Boolean,
    ): TrackingMembershipResolution? {
        val mediaId = aniListMediaIdOf(item.id) ?: return null
        val requestedStatus = aniListStatusTabs.firstOrNull { tab ->
            desiredMembership[tab.key] == true
        }

        val succeeded = if (requestedStatus == null) {
            // No status selected: drop the entry entirely. Without an entry id there is nothing on
            // the account to delete, so treat that as already done.
            val entryId = findEntry(item.id)?.id
            entryId == null || AniListMutations.deleteEntry(entryId)
        } else {
            AniListMutations.setStatus(mediaId = mediaId, status = requestedStatus.wireStatus)
        }

        if (succeeded) {
            // AniList is the source of truth for derived fields such as progress and completedAt,
            // so re-read rather than patching the snapshot locally.
            AniListListRepository.refresh(forceRefresh = true)
        }
        return null
    }

    private fun findEntry(contentId: String): AniListMediaListEntry? {
        val mediaId = aniListMediaIdOf(contentId) ?: return null
        val snapshot = AniListListRepository.snapshot()
        val source = if (contentId.startsWith(ANILIST_MANGA_ID_PREFIX, ignoreCase = true)) {
            snapshot.manga
        } else {
            snapshot.anime
        }
        return source.firstOrNull { entry -> entry.media?.id == mediaId }
    }
}

/**
 * Cheap identity for the parts of a lists snapshot the library actually renders. Deliberately
 * excludes `loadedAtEpochMs`, which changes on every fetch even when nothing else did.
 */
internal fun AniListListsSnapshot.contentFingerprint(): Int {
    var hash = 1
    fun fold(entries: List<AniListMediaListEntry>) {
        entries.forEach { entry ->
            hash = 31 * hash + (entry.id ?: 0)
            hash = 31 * hash + (entry.media?.id ?: 0)
            hash = 31 * hash + (entry.status?.hashCode() ?: 0)
            hash = 31 * hash + (entry.progress ?: 0)
        }
    }
    fold(anime)
    fold(manga)
    return hash
}

/** Extracts the AniList media id from either an `anilist:` or `anilist-manga:` content id. */
internal fun aniListMediaIdOf(contentId: String): Int? {
    val trimmed = contentId.trim()
    val raw = when {
        trimmed.startsWith(ANILIST_MANGA_ID_PREFIX, ignoreCase = true) ->
            trimmed.removePrefix(ANILIST_MANGA_ID_PREFIX)

        trimmed.startsWith(ANILIST_ID_PREFIX, ignoreCase = true) ->
            trimmed.removePrefix(ANILIST_ID_PREFIX)

        else -> return null
    }
    return raw.substringBefore(':').toIntOrNull()
}
