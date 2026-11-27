package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.ANILIST_MEDIA_FIELDS
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListPage
import com.nuvio.app.core.anilist.toMetaPreview
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.dedupeCatalogItems
import com.nuvio.app.features.anivio.enrichPreviewPostersWithAnivio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Content type used for the movie half of a search; anything else is treated as episodic. */
internal const val ANILIST_SEARCH_MOVIE_TYPE = "movie"

internal const val ANILIST_SEARCH_SERIES_TYPE = "series"
internal const val ANILIST_SEARCH_MANGA_TYPE = "manga"

/**
 * Title search against AniList.
 *
 * The term travels as a GraphQL variable rather than being spliced into the document, so a query
 * containing quotes or braces cannot break the request. `SEARCH_MATCH` is AniList's own relevance
 * ordering, which beats sorting by popularity for a typed query.
 */
internal object AniListSearchSource {
    suspend fun searchPage(
        query: String,
        contentType: String,
        page: Int,
        perPage: Int,
        forceRefresh: Boolean,
    ): CatalogPage {
        val result = searchPages(query, listOf(contentType), page, perPage, forceRefresh).getValue(contentType)
        return result.copy(items = enrichPreviewPostersWithAnivio(result.items))
    }

    suspend fun searchPages(
        query: String,
        contentTypes: List<String>,
        page: Int,
        perPage: Int,
        forceRefresh: Boolean,
    ): Map<String, CatalogPage> = withContext(Dispatchers.Default) {
        val boundedPerPage = perPage.coerceIn(1, ANILIST_MAX_PER_PAGE)
        val adultFilter = aniListAdultFilter(AniListCatalogSource.adultContentEnabled())
        val selections = contentTypes.mapIndexed { index, type ->
            searchSelection(index, type, page, boundedPerPage, adultFilter)
        }
        val data = AniListClient.query(
            query = "query (${'$'}search: String) {\n" + selections.joinToString("\n") + "\n}",
            variables = buildJsonObject { put("search", query) },
            forceRefresh = forceRefresh,
        )

        contentTypes.mapIndexed { index, type ->
            val parsed = (data["result$index"] as? JsonObject)?.let {
                AniListClient.json.decodeFromJsonElement(AniListPage.serializer(), it)
            }
            val media = parsed?.media.orEmpty()
            type to CatalogPage(
                items = dedupeCatalogItems(media.mapNotNull { it.toMetaPreview() }),
                rawItemCount = media.size,
                nextSkip = (page + 1).takeIf { parsed?.pageInfo?.hasNextPage == true && media.isNotEmpty() },
            )
        }.toMap()
    }

    private fun searchSelection(index: Int, type: String, page: Int, perPage: Int, adultFilter: String): String {
        val mediaType = if (type.equals(ANILIST_SEARCH_MANGA_TYPE, ignoreCase = true)) "MANGA" else "ANIME"
        val formatFilter = when (type.lowercase()) {
            ANILIST_SEARCH_MANGA_TYPE -> ""
            ANILIST_SEARCH_MOVIE_TYPE -> ", format_in: [MOVIE]"
            else -> ", format_not_in: [MOVIE]"
        }
        return """
            result$index: Page(page: $page, perPage: $perPage) {
                pageInfo { currentPage hasNextPage }
                media(type: $mediaType, search: ${'$'}search, sort: SEARCH_MATCH$formatFilter$adultFilter) {
                    $ANILIST_MEDIA_FIELDS
                }
            }
        """.trimIndent()
    }
}
