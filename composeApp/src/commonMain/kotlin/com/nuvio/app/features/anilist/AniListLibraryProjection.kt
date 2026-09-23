package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.anilist.aniListContentType
import com.nuvio.app.core.anilist.displayTitle
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_library_continue_reading
import nuvio.composeapp.generated.resources.anilist_library_continue_watching
import nuvio.composeapp.generated.resources.anilist_library_manga
import nuvio.composeapp.generated.resources.anilist_library_planned_anime
import nuvio.composeapp.generated.resources.anilist_library_planned_manga
import nuvio.composeapp.generated.resources.anilist_library_planned_ongoing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Manga ids are prefixed differently so `isAniListId` never routes one to the anime meta source. */
const val ANILIST_MANGA_ID_PREFIX = "anilist-manga:"

const val ANILIST_MANGA_TYPE = "manga"

private const val ANILIST_MEDIA_TYPE_MANGA = "MANGA"

/** In-progress covers REPEATING as well — a rewatch/reread is still something to continue. */
private val IN_PROGRESS_STATUSES = setOf(AniListListStatus.CURRENT, AniListListStatus.REPEATING)

internal data class AniListLibrarySectionDefinition(
    val key: String,
    val titleResource: StringResource,
    val isManga: Boolean,
    val matches: (AniListMediaListEntry) -> Boolean,
)

internal val aniListLibrarySectionDefinitions = listOf(
    AniListLibrarySectionDefinition(
        key = "anilist:continue-watching",
        titleResource = Res.string.anilist_library_continue_watching,
        isManga = false,
        matches = { entry -> entry.status in IN_PROGRESS_STATUSES },
    ),
    AniListLibrarySectionDefinition(
        key = "anilist:planned-ongoing",
        titleResource = Res.string.anilist_library_planned_ongoing,
        isManga = false,
        matches = { entry ->
            entry.status == AniListListStatus.PLANNING &&
                entry.media?.status == ANILIST_MEDIA_STATUS_RELEASING
        },
    ),
    AniListLibrarySectionDefinition(
        key = "anilist:planned-anime",
        titleResource = Res.string.anilist_library_planned_anime,
        isManga = false,
        matches = { entry -> entry.status == AniListListStatus.PLANNING },
    ),
    AniListLibrarySectionDefinition(
        key = "anilist:continue-reading",
        titleResource = Res.string.anilist_library_continue_reading,
        isManga = true,
        matches = { entry -> entry.status in IN_PROGRESS_STATUSES },
    ),
    AniListLibrarySectionDefinition(
        key = "anilist:manga",
        titleResource = Res.string.anilist_library_manga,
        isManga = true,
        // Every manga on the list, whatever its status.
        matches = { true },
    ),
    AniListLibrarySectionDefinition(
        key = "anilist:planned-manga",
        titleResource = Res.string.anilist_library_planned_manga,
        isManga = true,
        matches = { entry -> entry.status == AniListListStatus.PLANNING },
    ),
)

/**
 * Turns a lists snapshot into the six library rows. Empty sections are dropped rather than shown as
 * blank rails.
 */
fun aniListLibraryProjection(snapshot: AniListListsSnapshot): List<LibrarySection> =
    aniListLibrarySectionDefinitions.mapNotNull { definition ->
        val source = if (definition.isManga) snapshot.manga else snapshot.anime
        val items = source
            .asSequence()
            .filter(definition.matches)
            .mapNotNull { entry -> entry.toLibraryItem(definition.key) }
            .distinctBy { item -> item.id }
            .sortedByDescending(LibraryItem::savedAtEpochMs)
            .toList()

        items.takeIf { it.isNotEmpty() }?.let { sectionItems ->
            LibrarySection(
                type = definition.key,
                displayTitle = runBlocking { getString(definition.titleResource) },
                items = sectionItems,
            )
        }
    }

/**
 * The status groups the AniList lists browser shows, in tab order. CURRENT and REPEATING are one
 * "Watching" tab — a rewatch is still watching. Manga is left out; this drives the anime lists view.
 */
enum class AniListListTab(val statuses: Set<String>) {
    WATCHING(setOf(AniListListStatus.CURRENT, AniListListStatus.REPEATING)),
    COMPLETED(setOf(AniListListStatus.COMPLETED)),
    PAUSED(setOf(AniListListStatus.PAUSED)),
    DROPPED(setOf(AniListListStatus.DROPPED)),
    PLANNING(setOf(AniListListStatus.PLANNING)),
}

/** Anime entries for one status tab, newest first, mapped to library items. Pure — no fetch. */
fun aniListEntriesForTab(snapshot: AniListListsSnapshot, tab: AniListListTab): List<LibraryItem> =
    snapshot.anime
        .asSequence()
        .filter { entry -> entry.status in tab.statuses }
        .mapNotNull { entry -> entry.toLibraryItem("anilist:${tab.name.lowercase()}") }
        .distinctBy { item -> item.id }
        .sortedByDescending(LibraryItem::savedAtEpochMs)
        .toList()

private fun AniListMediaListEntry.toLibraryItem(sectionKey: String): LibraryItem? {
    val media = media ?: return null
    val name = media.displayTitle() ?: return null
    val poster = media.coverImage?.extraLarge?.takeIf { it.isNotBlank() }
        ?: media.coverImage?.large?.takeIf { it.isNotBlank() }
        ?: return null

    val isManga = media.type?.uppercase() == ANILIST_MEDIA_TYPE_MANGA
    return LibraryItem(
        id = if (isManga) "$ANILIST_MANGA_ID_PREFIX${media.id}" else "$ANILIST_ID_PREFIX${media.id}",
        type = if (isManga) ANILIST_MANGA_TYPE else media.aniListContentType(),
        name = name,
        poster = poster,
        banner = media.bannerImage?.takeIf { it.isNotBlank() },
        releaseInfo = (media.seasonYear ?: media.startDate?.year)?.toString(),
        imdbRating = media.averageScore?.takeIf { it > 0 }?.let { (it / 10.0).toString() },
        genres = media.genres,
        // Plain anime cards, same as every other row on the library and home screens.
        posterShape = PosterShape.Poster,
        listKeys = setOf(sectionKey),
        mediaCategory = if (isManga) ANILIST_MANGA_TYPE else "anime",
        trackingProviderId = "anilist",
        trackingProviderItemId = id?.toString(),
        trackingSourceUrl = media.aniListSiteUrl(isManga),
        savedAtEpochMs = updatedAt?.takeIf { it > 0 }?.times(1_000L) ?: 0L,
    )
}

/** Where a tap goes for manga, which has no in-app page yet. */
private fun com.nuvio.app.core.anilist.AniListMedia.aniListSiteUrl(isManga: Boolean): String =
    "https://anilist.co/${if (isManga) "manga" else "anime"}/$id"
