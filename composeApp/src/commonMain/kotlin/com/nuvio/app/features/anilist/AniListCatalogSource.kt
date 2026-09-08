package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.AniListAiringPage
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListMedia
import com.nuvio.app.core.anilist.AniListPage
import com.nuvio.app.core.anilist.toMetaPreview
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.dedupeCatalogItems
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Resolves AniList home rows and catalog pages straight to [CatalogPage], the same way
 * `TmdbCollectionSourceResolver` and `TraktPublicListSourceResolver` do for their sources.
 *
 * `nextSkip` carries the **next page number**, not an item offset — matching the convention
 * `CatalogRepository` already uses for collection sources.
 */
object AniListCatalogSource {
    private const val ALIAS_PREFIX = "row"

    private val log = Logger.withTag("AniListCatalogSource")

    /**
     * Fetches one row. Used by the catalog screen and as the per-row fallback for home.
     *
     * When [searchQuery] is set this resolves a search page instead of a named catalog, which is how
     * "see all" on a search result row pages through AniList.
     */
    suspend fun resolve(
        catalogId: String,
        page: Int = 1,
        maxItems: Int? = null,
        forceRefresh: Boolean = false,
        searchQuery: String? = null,
    ): CatalogPage = withContext(Dispatchers.Default) {
        val trimmedSearch = searchQuery?.trim()?.takeIf { it.isNotEmpty() }
        if (trimmedSearch != null) {
            return@withContext AniListSearchSource.searchPage(
                query = trimmedSearch,
                contentType = catalogId,
                page = page,
                perPage = maxItems ?: ANILIST_MAX_PER_PAGE,
                forceRefresh = forceRefresh,
            )
        }

        val catalog = aniListCatalog(catalogId) ?: error("Unknown AniList catalog: $catalogId")
        val nowSeconds = aniListNowBucketSeconds()
        val perPage = if (maxItems == null) ANILIST_MAX_PER_PAGE else catalog.homePerPage
        val alias = ALIAS_PREFIX + "0"
        val includeAdult = adultContentEnabled()

        val data = AniListClient.query(
            query = "query {\n" +
                catalog.pageSelection(alias, page, perPage, nowSeconds, includeAdult) +
                "\n}",
            forceRefresh = forceRefresh,
        )

        data.readCatalogPage(alias, catalog, page, maxItems, includeAdult)
            ?: CatalogPage(items = emptyList(), rawItemCount = 0, nextSkip = null)
    }

    internal fun adultContentEnabled(): Boolean =
        HomeCatalogSettingsRepository.snapshot().adultContentEnabled

    /**
     * Fetches every requested row in a **single** aliased GraphQL query. AniList is limited to 30
     * requests/minute, so collapsing five home rows into one request is what keeps a pull-to-refresh
     * from eating a sixth of the budget.
     */
    suspend fun resolveHomeRows(
        catalogIds: List<String>,
        maxItems: Int? = null,
        forceRefresh: Boolean = false,
    ): Map<String, CatalogPage> = withContext(Dispatchers.Default) {
        val catalogs = catalogIds.mapNotNull(::aniListCatalog)
        if (catalogs.isEmpty()) return@withContext emptyMap()

        val nowSeconds = aniListNowBucketSeconds()
        val includeAdult = adultContentEnabled()
        val selections = catalogs.mapIndexed { index, catalog ->
            catalog.pageSelection(
                alias = ALIAS_PREFIX + index,
                page = 1,
                perPage = catalog.homePerPage,
                nowSeconds = nowSeconds,
                includeAdult = includeAdult,
            )
        }

        val data = AniListClient.query(
            query = "query {\n" + selections.joinToString(separator = "\n") + "\n}",
            forceRefresh = forceRefresh,
        )

        catalogs.mapIndexedNotNull { index, catalog ->
            val page = data.readCatalogPage(
                alias = ALIAS_PREFIX + index,
                catalog = catalog,
                requestedPage = 1,
                maxItems = maxItems,
                includeAdult = includeAdult,
            ) ?: return@mapIndexedNotNull null
            catalog.id to page
        }.toMap()
    }

    private fun AniListCatalog.pageSelection(
        alias: String,
        page: Int,
        perPage: Int,
        nowSeconds: Long,
        includeAdult: Boolean,
    ): String =
        """
        $alias: Page(page: $page, perPage: ${perPage.coerceIn(1, ANILIST_MAX_PER_PAGE)}) {
            pageInfo { currentPage hasNextPage }
            ${selection(nowSeconds, includeAdult)}
        }
        """.trimIndent()

    private fun JsonObject.readCatalogPage(
        alias: String,
        catalog: AniListCatalog,
        requestedPage: Int,
        maxItems: Int?,
        includeAdult: Boolean,
    ): CatalogPage? {
        val element = this[alias] as? JsonObject ?: return null

        return runCatching {
            val media: List<AniListMedia>
            val hasNextPage: Boolean
            val rawItemCount: Int

            if (catalog.isAiringFeed) {
                val airingPage = AniListClient.json.decodeFromJsonElement(
                    AniListAiringPage.serializer(),
                    element,
                )
                // One entry per aired episode, so the same show recurs — collapse to unique titles.
                // `airingSchedules` takes no `isAdult` filter, unlike the `media` rows, so the
                // adult exclusion has to happen here.
                media = airingPage.airingSchedules
                    .mapNotNull { it.media }
                    .filter { includeAdult || !it.isAdult }
                    .distinctBy { it.id }
                hasNextPage = airingPage.pageInfo?.hasNextPage == true
                rawItemCount = airingPage.airingSchedules.size
            } else {
                val mediaPage = AniListClient.json.decodeFromJsonElement(
                    AniListPage.serializer(),
                    element,
                )
                media = mediaPage.media
                hasNextPage = mediaPage.pageInfo?.hasNextPage == true
                rawItemCount = mediaPage.media.size
            }

            val items: List<MetaPreview> = dedupeCatalogItems(media.mapNotNull { it.toMetaPreview() })
                .let { previews -> if (maxItems == null) previews else previews.take(maxItems) }

            CatalogPage(
                items = items,
                rawItemCount = rawItemCount,
                nextSkip = (requestedPage + 1).takeIf { hasNextPage && rawItemCount > 0 },
            )
        }.onFailure { error ->
            log.w(error) { "Failed to parse AniList row ${catalog.id}" }
        }.getOrNull()
    }
}
