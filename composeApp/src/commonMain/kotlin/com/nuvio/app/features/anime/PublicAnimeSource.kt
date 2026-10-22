package com.nuvio.app.features.anime

import com.nuvio.app.core.anilist.AniListUnavailableException
import com.nuvio.app.features.anilist.AniListCatalogSource
import com.nuvio.app.features.anilist.AniListMetaSource
import com.nuvio.app.features.anilist.isAniListMangaId
import com.nuvio.app.features.anivio.AnivioMetaSource
import com.nuvio.app.features.anivio.AnivioPublicAnimeProvider
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
    ): CatalogPage = PublicAnimeRouter.routeWithOptionalFallback(
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
            AnivioPublicAnimeProvider.catalog(
                catalogId = catalogId,
                contentType = contentType,
                page = page,
                maxItems = maxItems,
                searchQuery = searchQuery,
                forceRefresh = forceRefresh,
            ) ?: provider?.catalog(catalogId, contentType, page, maxItems, searchQuery, forceRefresh)
                ?: throw AniListUnavailableException(503)
        },
    )

    suspend fun homeRows(
        catalogIds: List<String>,
        maxItems: Int? = null,
        forceRefresh: Boolean = false,
    ): Map<String, CatalogPage> = PublicAnimeRouter.routeWithOptionalFallback(
        forceProbe = forceRefresh,
        aniList = { AniListCatalogSource.resolveHomeRows(catalogIds, maxItems, forceRefresh) },
        fallback = { provider ->
            val anivioRows = AnivioPublicAnimeProvider.homeRows(catalogIds, maxItems, forceRefresh)
            if (provider == null || anivioRows.keys.containsAll(catalogIds.distinct())) {
                anivioRows
            } else {
                anivioRows + provider.homeRows(
                    catalogIds = catalogIds.filterNot(anivioRows::containsKey),
                    maxItems = maxItems,
                    forceRefresh = forceRefresh,
                )
            }
        },
    )

    suspend fun discover(
        page: Int,
        contentType: String,
        genre: String?,
        sort: String,
        forceRefresh: Boolean,
        aniListFetch: suspend () -> CatalogPage,
    ): CatalogPage = PublicAnimeRouter.routeWithOptionalFallback(
        forceProbe = forceRefresh,
        aniList = aniListFetch,
        fallback = { provider ->
            AnivioPublicAnimeProvider.discover(
                page = page,
                contentType = contentType,
                genre = genre,
                sort = sort,
                forceRefresh = forceRefresh,
            ) ?: provider?.discover(page, contentType, genre, sort, forceRefresh)
                ?: throw AniListUnavailableException(503)
        },
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
        // Manga has an AniList-native identity (`anilist-manga:<mediaId>`), not an IMDb/TMDB/MAL
        // identity. It must never be handed to the anime fallback router.
        if (contentType.equals("manga", ignoreCase = true) || isAniListMangaId(id)) {
            return AniListMetaSource.fetchMeta(
                id = id,
                type = "manga",
                forceRefresh = forceRefresh,
                onPartialMeta = onPartialMeta,
            )?.let { PublicAnimeMetaResult(it.meta, it.externalFallbackId) }
        }
        if (id.startsWith("mal:", ignoreCase = true)) {
            return PublicAnimeRouter.fallbackOnly { it.details(id, contentType, forceRefresh) }
        }
        val aniListFetch = suspend {
            AniListMetaSource.fetchMeta(
                id = id,
                type = contentType,
                forceRefresh = forceRefresh,
                onPartialMeta = onPartialMeta,
            )?.let {
                PublicAnimeMetaResult(it.meta, it.externalFallbackId)
            }
        }
        return try {
            PublicAnimeRouter.routeWithOptionalFallback(
                forceProbe = forceRefresh,
                aniList = aniListFetch,
                fallback = { provider ->
                    val anivio = AnivioMetaSource.fetchMeta(id, contentType, forceRefresh)
                    anivio?.let { PublicAnimeMetaResult(it, null) }
                        ?: provider?.details(id, contentType, forceRefresh)
                        ?: throw AniListUnavailableException(503)
                },
            )
        } catch (error: AniListUnavailableException) {
            AnivioMetaSource.fetchMeta(id, contentType, forceRefresh)
                ?.let { PublicAnimeMetaResult(it, null) }
                ?: throw error
        }
    }
}
