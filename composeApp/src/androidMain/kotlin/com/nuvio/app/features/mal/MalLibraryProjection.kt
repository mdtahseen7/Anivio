package com.nuvio.app.features.mal

import com.nuvio.app.core.mal.MalAnimeListEntry
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.library.LibraryItem
import com.nuvio.app.features.library.LibrarySection

internal data class MalLibrarySectionDefinition(
    val status: String,
    val title: String,
)

internal val malLibrarySections = listOf(
    MalLibrarySectionDefinition(MalListStatusValue.WATCHING, "Watching"),
    MalLibrarySectionDefinition(MalListStatusValue.PLAN_TO_WATCH, "Plan to Watch"),
    MalLibrarySectionDefinition(MalListStatusValue.COMPLETED, "Completed"),
    MalLibrarySectionDefinition(MalListStatusValue.ON_HOLD, "On Hold"),
    MalLibrarySectionDefinition(MalListStatusValue.DROPPED, "Dropped"),
)

internal fun malLibraryProjection(snapshot: MalListsSnapshot): List<LibrarySection> =
    malLibrarySections.mapNotNull { definition ->
        val items = snapshot.entries
            .asSequence()
            .filter { it.listStatus.status.equals(definition.status, ignoreCase = true) }
            .map(MalAnimeListEntry::toLibraryItem)
            .distinctBy(LibraryItem::id)
            .sortedByDescending(LibraryItem::savedAtEpochMs)
            .toList()
        items.takeIf(List<*>::isNotEmpty)?.let {
            LibrarySection(type = "mal:status:${definition.status}", displayTitle = definition.title, items = it)
        }
    }

internal fun MalAnimeListEntry.toLibraryItem(): LibraryItem = LibraryItem(
    id = "$MAL_ID_PREFIX${node.id}",
    type = malContentType(),
    name = node.alternativeTitles?.en?.takeIf(String::isNotBlank) ?: node.title,
    poster = node.mainPicture?.large?.takeIf(String::isNotBlank) ?: node.mainPicture?.medium,
    releaseInfo = node.startDate?.take(4),
    imdbRating = node.mean?.takeIf { it > 0.0 }?.toString(),
    genres = node.genres.map { it.name },
    posterShape = PosterShape.Poster,
    listKeys = setOf("mal:status:${listStatus.status.lowercase()}"),
    mediaCategory = "anime",
    trackingProviderId = "mal",
    trackingProviderItemId = node.id.toString(),
    trackingSourceUrl = "https://myanimelist.net/anime/${node.id}",
    savedAtEpochMs = malUpdatedAtEpochMs(listStatus.updatedAt),
)

internal fun MalAnimeListEntry.malContentType(): String =
    if (node.mediaType.equals("movie", ignoreCase = true)) "movie" else "series"

internal fun malUpdatedAtEpochMs(value: String?): Long =
    value?.let { com.nuvio.app.core.time.parseZonedIsoDateTimeToEpochMs(it) } ?: 0L
