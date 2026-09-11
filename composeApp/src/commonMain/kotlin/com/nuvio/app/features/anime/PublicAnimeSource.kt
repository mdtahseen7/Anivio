package com.nuvio.app.features.anime

import com.nuvio.app.features.anilist.AniListCatalogSource
import com.nuvio.app.features.anilist.AniListMetaSource
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.details.MetaDetails

/** Provider-neutral entry point used by public anime screens and repositories. */
object PublicAnimeSource {
    suspend fun catalog(
        catalogId: String,
        contentType: String,
        page: Int = 1,
        maxItems: Int? = null,
        forceRefresh: Boolean = false,
        searchQuery: String? = null,
    ): CatalogPage = PublicAnimeRouter.route(
        forceProbe = forceRefresh,
        aniList = {
            AniListCatalogSource.resolve(
                catalogId = catalogId,
                page = page,
                maxItems = maxItems,
                forceRefresh = forceRefresh,
                searchQuery = searchQuery,
            )
        },
        fallback = { provider ->
            provider.catalog(catalogId, contentType, page, maxItems, searchQuery, forceRefresh)
        },
    )

    suspend fun homeRows(
        catalogIds: List<String>,
        maxItems: Int? = null,
        forceRefresh: Boolean = false,
    ): Map<String, CatalogPage> = PublicAnimeRouter.route(
        forceProbe = forceRefresh,
        aniList = { AniListCatalogSource.resolveHomeRows(catalogIds, maxItems, forceRefresh) },
        fallback = { it.homeRows(catalogIds, maxItems, forceRefresh) },
    )

    suspend fun discover(
        page: Int,
        contentType: String,
        genre: String?,
        sort: String,
        forceRefresh: Boolean,
        aniListFetch: suspend () -> CatalogPage,
    ): CatalogPage = PublicAnimeRouter.route(
        forceProbe = forceRefresh,
        aniList = aniListFetch,
        fallback = { provider -> provider.discover(page, contentType, genre, sort, forceRefresh) },
    )

    /**
     * @param onPartialMeta called with a renderable-but-unenriched result when the full one is slow
     * enough to be worth pre-empting. Only the AniList path reports progress; the MAL fallback
     * returns in one shot.
     */
    suspend fun details(
        id: String,
        contentType: String,
        forceRefresh: Boolean = false,
        onPartialMeta: ((MetaDetails) -> Unit)? = null,
    ): PublicAnimeMetaResult? {
        if (id.startsWith("mal:", ignoreCase = true)) {
            return PublicAnimeRouter.fallbackOnly { it.details(id, contentType, forceRefresh) }
        }
        return PublicAnimeRouter.route(
            forceProbe = forceRefresh,
            aniList = {
                AniListMetaSource.fetchMeta(
                    id = id,
                    type = contentType,
                    forceRefresh = forceRefresh,
                    onPartialMeta = onPartialMeta,
                )?.let {
                    PublicAnimeMetaResult(it.meta, it.externalFallbackId)
                }
            },
            fallback = { it.details(id, contentType, forceRefresh) },
        )
    }
}
