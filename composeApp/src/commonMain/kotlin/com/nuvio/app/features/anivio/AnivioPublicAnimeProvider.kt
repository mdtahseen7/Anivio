package com.nuvio.app.features.anivio

import com.nuvio.app.core.anivio.AnivioCatalogQuery
import com.nuvio.app.core.anivio.AnivioClient
import com.nuvio.app.core.anivio.AnivioMedia
import com.nuvio.app.core.anivio.AnivioMediaSort
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import kotlinx.coroutines.CancellationException

/**
 * Provider-neutral, metadata-only catalog adapter for Anivio.
 *
 * It intentionally uses only Anivio's bounded list queries. No per-card detail requests are made,
 * and unsupported catalog shapes return null so the existing MAL/addon fallback can continue.
 */
object AnivioPublicAnimeProvider {
    private const val MAX_LIST_ITEMS = 20

    suspend fun catalog(
        catalogId: String,
        contentType: String,
        page: Int,
        maxItems: Int?,
        searchQuery: String?,
        forceRefresh: Boolean,
    ): CatalogPage? {
        if (!isEnabled() || contentType.equals("manga", ignoreCase = true)) return null

        val requestedLimit = (maxItems ?: MAX_LIST_ITEMS).coerceIn(1, MAX_LIST_ITEMS)
        val media = try {
            if (!searchQuery.isNullOrBlank()) {
                AnivioClient.search(searchQuery, page = page, forceRefresh = forceRefresh)
            } else {
                catalogQuery(catalogId)?.let { query ->
                    AnivioClient.catalog(query.copy(page = page), forceRefresh = forceRefresh)
                } ?: return null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        }

        val filtered = media.filter { it.matchesContentType(contentType) }
        return CatalogPage(
            items = filtered.mapNotNull { it.toMetaPreview(contentType) }.take(requestedLimit),
            // Keep pagination based on the server's bounded response, not pageInfo or the filtered
            // card count. A movie/series filter may legitimately leave fewer than 20 visible cards.
            rawItemCount = media.size,
            nextSkip = (page.coerceAtLeast(1) + 1).takeIf { media.size >= MAX_LIST_ITEMS },
        )
    }

    suspend fun homeRows(
        catalogIds: List<String>,
        maxItems: Int?,
        forceRefresh: Boolean,
    ): Map<String, CatalogPage> {
        if (!isEnabled()) return emptyMap()

        // Keep home refresh bounded and predictable on low-spec hosts. Each row is already one
        // lightweight GraphQL request; do not fan out detail/artwork requests per item.
        val rows = linkedMapOf<String, CatalogPage>()
        for (catalogId in catalogIds.distinct()) {
            val page = catalog(
                catalogId = catalogId,
                contentType = catalogContentType(catalogId),
                page = 1,
                maxItems = maxItems,
                searchQuery = null,
                forceRefresh = forceRefresh,
            ) ?: continue
            rows[catalogId] = page
        }
        return rows
    }

    suspend fun discover(
        page: Int,
        contentType: String,
        genre: String?,
        sort: String,
        forceRefresh: Boolean,
    ): CatalogPage? {
        if (!isEnabled() || contentType.equals("manga", ignoreCase = true)) return null
        val mediaSort = when (sort) {
            "trending" -> AnivioMediaSort.TRENDING_DESC
            "popular" -> AnivioMediaSort.POPULARITY_DESC
            "top-rated" -> AnivioMediaSort.SCORE_DESC
            "newest" -> AnivioMediaSort.START_DATE_DESC
            // Anivio's bounded catalog client does not expose a status filter. Let MAL handle this
            // one rather than pretending a newest list is an upcoming list.
            "upcoming" -> return null
            else -> AnivioMediaSort.POPULARITY_DESC
        }
        return fetchDiscoverPage(page, contentType, genre, mediaSort, forceRefresh)
    }

    private suspend fun fetchDiscoverPage(
        page: Int,
        contentType: String,
        genre: String?,
        sort: AnivioMediaSort,
        forceRefresh: Boolean,
    ): CatalogPage? {
        val media = try {
            AnivioClient.catalog(
                query = AnivioCatalogQuery(
                    page = page,
                    sort = sort,
                    genre = genre?.takeIf(String::isNotBlank),
                ),
                forceRefresh = forceRefresh,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        }
        return CatalogPage(
            items = media.filter { it.matchesContentType(contentType) }
                .mapNotNull { it.toMetaPreview(contentType) },
            rawItemCount = media.size,
            nextSkip = (page.coerceAtLeast(1) + 1).takeIf { media.size >= MAX_LIST_ITEMS },
        )
    }

    private suspend fun isEnabled(): Boolean {
        TrackingSettingsRepository.ensureLoaded()
        return TrackingSettingsRepository.uiState.value.anivioMetadataFallbackEnabled
    }

    private fun catalogQuery(catalogId: String): AnivioCatalogQuery? = when (catalogId) {
        "trending" -> AnivioCatalogQuery(sort = AnivioMediaSort.TRENDING_DESC)
        "popular" -> AnivioCatalogQuery(sort = AnivioMediaSort.POPULARITY_DESC)
        "top-rated" -> AnivioCatalogQuery(sort = AnivioMediaSort.SCORE_DESC)
        "newest", "recently-released" -> AnivioCatalogQuery(sort = AnivioMediaSort.START_DATE_DESC)
        "movies" -> AnivioCatalogQuery(sort = AnivioMediaSort.POPULARITY_DESC)
        // The Anivio client deliberately does not expose pageInfo/status filters, so this cannot
        // faithfully represent AniList's upcoming row.
        "upcoming" -> null
        else -> null
    }

    private fun catalogContentType(catalogId: String): String =
        if (catalogId == "movies") "movie" else "series"

    private fun AnivioMedia.matchesContentType(contentType: String): Boolean = when {
        contentType.equals("movie", ignoreCase = true) -> format.equals("MOVIE", ignoreCase = true)
        contentType.equals("series", ignoreCase = true) -> !format.equals("MOVIE", ignoreCase = true)
        else -> true
    }

    private fun AnivioMedia.toMetaPreview(requestedType: String): MetaPreview? {
        val titleText = title?.english ?: title?.romaji ?: title?.native ?: return null
        return MetaPreview(
            id = "anilist:$id",
            type = if (format.equals("MOVIE", ignoreCase = true)) "movie" else requestedType,
            name = titleText,
            poster = coverImage?.large ?: coverImage?.medium,
            posterShape = PosterShape.Poster,
            releaseInfo = seasonYear?.toString(),
            popularity = popularity?.toDouble(),
            imdbRating = averageScore?.takeIf { it > 0 }?.let { (it / 10.0).toString() },
            airingStatus = status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
        )
    }
}
