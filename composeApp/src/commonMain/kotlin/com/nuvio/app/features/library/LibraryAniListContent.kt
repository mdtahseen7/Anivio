package com.nuvio.app.features.library

import androidx.compose.foundation.lazy.LazyListScope
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.HomeCatalogRowSection

/**
 * The AniList library rows, built from the very same [HomeCatalogRowSection] the home screen uses —
 * so the card style, spacing and theme options in Settings apply here without a second
 * implementation to keep in step.
 */

/** Section keys from `aniListLibraryProjection`, in the order the library shows them. */
private val ANILIST_LIBRARY_ROW_ORDER = listOf(
    "anilist:continue-watching",
    "anilist:planned-ongoing",
    "anilist:planned-anime",
    "anilist:continue-reading",
    "anilist:planned-manga",
)

internal fun LibrarySection.toHomeCatalogSection(): HomeCatalogSection {
    val previews = items.map(LibraryItem::toMetaPreview)
    return HomeCatalogSection(
        key = type,
        title = displayTitle,
        subtitle = displayTitle,
        addonName = "AniList",
        target = CatalogTarget.Library(contentType = "series", sectionType = type),
        items = previews,
        availableItemCount = previews.size,
        hasMore = false,
    )
}

internal fun LazyListScope.aniListLibraryContent(
    sections: List<LibrarySection>,
    watchedKeys: Set<String>,
    fullyWatchedSeriesKeys: Set<String>,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    val sectionsByKey = sections.associateBy(LibrarySection::type)

    ANILIST_LIBRARY_ROW_ORDER.forEach { key ->
        val section = sectionsByKey[key] ?: return@forEach
        if (section.items.isEmpty()) return@forEach
        item(key = "anilist-library-row:$key") {
            HomeCatalogRowSection(
                section = section.toHomeCatalogSection(),
                watchedKeys = watchedKeys,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
            )
        }
    }
}
