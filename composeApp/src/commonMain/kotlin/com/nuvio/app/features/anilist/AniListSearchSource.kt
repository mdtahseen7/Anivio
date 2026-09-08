package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.ANILIST_MEDIA_FIELDS
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListPage
import com.nuvio.app.core.anilist.toMetaPreview
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.dedupeCatalogItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Content type used for the movie half of a search; anything else is treated as episodic. */
internal const val ANILIST_SEARCH_MOVIE_TYPE = "movie"

internal const val ANILIST_SEARCH_SERIES_TYPE = "series"

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
    ): CatalogPage = withContext(Dispatchers.Default) {
        val formatFilter = if (contentType.equals(ANILIST_SEARCH_MOVIE_TYPE, ignoreCase = true)) {
            ", format_in: [MOVIE]"
        } else {
            ", format_not_in: [MOVIE]"
        }
        val adultFilter = aniListAdultFilter(AniListCatalogSource.adultContentEnabled())
        val boundedPerPage = perPage.coerceIn(1, ANILIST_MAX_PER_PAGE)

        val document = """
            query (${'$'}search: String) {
                Page(page: $page, perPage: $boundedPerPage) {
                    pageInfo { currentPage hasNextPage }
                    media(
                        type: ANIME,
                        search: ${'$'}search,
                        sort: SEARCH_MATCH$formatFilter$adultFilter
                    ) {
                        $ANILIST_MEDIA_FIELDS
                    }
                }
            }
        """.trimIndent()

        val data = AniListClient.query(
            query = document,
            variables = buildJsonObject { put("search", query) },
            forceRefresh = forceRefresh,
        )

        val pageObject = data["Page"] as? JsonObject
            ?: return@withContext CatalogPage(items = emptyList(), rawItemCount = 0, nextSkip = null)
        val parsed = AniListClient.json.decodeFromJsonElement(AniListPage.serializer(), pageObject)
        val items = dedupeCatalogItems(parsed.media.mapNotNull { it.toMetaPreview() })

        CatalogPage(
            items = items,
            rawItemCount = parsed.media.size,
            nextSkip = (page + 1).takeIf {
                parsed.pageInfo?.hasNextPage == true && parsed.media.isNotEmpty()
            },
        )
    }
}
