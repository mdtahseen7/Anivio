package com.nuvio.app.features.mal

import com.nuvio.app.core.anilist.AniZipClient
import com.nuvio.app.core.anilist.artworkUrl
import com.nuvio.app.core.anilist.clearLogoUrl
import com.nuvio.app.core.anilist.fanartUrl
import com.nuvio.app.core.anilist.KitsuAnime
import com.nuvio.app.core.anilist.KitsuClient
import com.nuvio.app.core.anilist.imdbId
import com.nuvio.app.core.anilist.malId
import com.nuvio.app.core.mal.MalPublicAnimeNode
import com.nuvio.app.core.mal.MalAnimePage
import com.nuvio.app.core.mal.MalClient
import com.nuvio.app.features.anime.AnimeEnrichment
import com.nuvio.app.features.anime.PublicAnimeFallbackProvider
import com.nuvio.app.features.anime.PublicAnimeMetaResult
import com.nuvio.app.features.anime.buildEnrichedEpisodes
import com.nuvio.app.features.anime.loadAnimeEnrichment
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.details.MetaCompany
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaLink
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.formatRuntimeFromMinutes
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import com.nuvio.app.features.anilist.ANILIST_SEASON
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Android public MAL v2 adapter. It is independent of account/auth state and only needs client id. */
object MalPublicAnimeProvider : PublicAnimeFallbackProvider {
    private const val PAGE_SIZE = 50
    private const val DETAIL_FIELDS = "id,title,main_picture,alternative_titles,start_date,end_date," +
        "synopsis,mean,popularity,num_list_users,media_type,status,genres,num_episodes," +
        "average_episode_duration,studios,pictures,start_season"

    fun install() = Unit

    override suspend fun catalog(
        catalogId: String,
        contentType: String,
        page: Int,
        maxItems: Int?,
        searchQuery: String?,
        forceRefresh: Boolean,
    ): CatalogPage {
        val limit = (maxItems ?: PAGE_SIZE).coerceIn(1, PAGE_SIZE)
        val offset = (page.coerceAtLeast(1) - 1) * limit
        // Kitsu owns these two rows even while MAL is the active provider: it publishes a real
        // trending list and an airing-filtered recency sort, where MAL only has ranking types that
        // are all popularity-shaped. A Kitsu miss falls through to MAL rather than emptying the row.
        if (searchQuery.isNullOrBlank()) {
            kitsuCatalog(catalogId, contentType, limit, offset, page, forceRefresh)
                ?.let { return it }
        }
        val response = if (!searchQuery.isNullOrBlank()) {
            MalClient.getPublic<MalAnimePage>(
                path = "anime",
                clientId = MalConfig.CLIENT_ID,
                query = MalClient.pageQuery(limit, offset) + mapOf(
                    "q" to searchQuery.trim(),
                    "fields" to LIST_FIELDS,
                ),
            )
        } else {
            val (path, extra) = catalogRequest(catalogId)
            MalClient.getPublic<MalAnimePage>(
                path = path,
                clientId = MalConfig.CLIENT_ID,
                query = MalClient.pageQuery(limit, offset) + extra + mapOf("fields" to LIST_FIELDS),
            )
        }
        return response.toCatalogPage(page, contentType, forceRefresh)
    }

    override suspend fun homeRows(
        catalogIds: List<String>,
        maxItems: Int?,
        forceRefresh: Boolean,
    ): Map<String, CatalogPage> = coroutineScope {
        catalogIds.distinct().map { id ->
            async { id to catalog(id, catalogContentType(id), 1, maxItems, null, forceRefresh) }
        }.awaitAll().toMap()
    }

    override suspend fun discover(
        page: Int,
        contentType: String,
        genre: String?,
        sort: String,
        forceRefresh: Boolean,
    ): CatalogPage {
        // MAL has no public browse-by-genre endpoint. Preserve honest paging and approximate sorts
        // using ranking; unsupported genre filters intentionally return an empty page.
        if (!genre.isNullOrBlank()) return CatalogPage(emptyList(), 0, null)
        // Every Discover sort key needs its own branch. "trending" previously fell through to the
        // else, so Trending and Popular issued the identical bypopularity request and showed the
        // same entries in the same order.
        val catalog = when (sort) {
            "upcoming" -> "upcoming"
            "newest" -> "recently-released"
            "top-rated" -> "top-rated"
            "trending" -> "trending"
            "popular" -> if (contentType == "movie") "movies" else "popular"
            else -> if (contentType == "movie") "movies" else "popular"
        }
        return catalog(catalog, contentType, page, PAGE_SIZE, null, forceRefresh)
    }

    override suspend fun details(
        id: String,
        contentType: String,
        forceRefresh: Boolean,
    ): PublicAnimeMetaResult? {
        val malId = resolveMalId(id, forceRefresh) ?: return null
        val anime = MalClient.getPublic<MalPublicAnimeNode>(
            path = "anime/$malId",
            clientId = MalConfig.CLIENT_ID,
            query = mapOf("fields" to DETAIL_FIELDS),
        )
        // The same ani.zip document that canonicalises the id also carries the episode and artwork
        // data MAL lacks, so fetch it once and hand it to the shared enrichment.
        val aniZip = AniZipClient.mappingsByMalId(malId, forceRefresh)
        // AniZipClient caches, so reusing the helper costs nothing and keeps one definition of how
        // a MAL id becomes a canonical app id.
        val canonicalId = canonicalId(malId, forceRefresh)
        val enrichment = loadAnimeEnrichment(
            aniZip = aniZip,
            isMovie = anime.contentType(contentType) == "movie",
            expectedEpisodeCount = anime.episodeCount?.takeIf { it > 0 },
        )
        return PublicAnimeMetaResult(
            meta = anime.toMetaDetails(canonicalId, contentType, enrichment),
            // Lets the app fall through to its external metadata path exactly as the AniList
            // provider does, instead of dead-ending on MAL's thin record.
            externalFallbackId = aniZip?.imdbId(),
        )
    }

    private suspend fun MalAnimePage.toCatalogPage(
        page: Int,
        requestedType: String,
        forceRefresh: Boolean,
    ): CatalogPage {
        val previews = coroutineScope {
            data.map { entry -> async { entry.node.toPreview(requestedType, forceRefresh) } }
                .awaitAll().filterNotNull()
        }
        return CatalogPage(
            items = previews,
            rawItemCount = data.size,
            nextSkip = (page + 1).takeIf { paging.next != null && data.isNotEmpty() },
        )
    }

    private suspend fun MalPublicAnimeNode.toPreview(requestedType: String, forceRefresh: Boolean): MetaPreview? {
        val poster = mainPicture?.large ?: mainPicture?.medium ?: return null
        return MetaPreview(
            id = canonicalId(id, forceRefresh),
            type = contentType(requestedType),
            name = alternativeTitles?.en?.takeIf(String::isNotBlank) ?: title,
            poster = poster,
            posterShape = PosterShape.Poster,
            description = synopsis,
            releaseInfo = startDate?.take(4),
            rawReleaseDate = startDate,
            popularity = numListUsers?.toDouble() ?: popularity?.let { 1_000_000.0 / it.coerceAtLeast(1) },
            imdbRating = score?.takeIf { it > 0 }?.toString(),
            genres = genres.map { it.name },
        )
    }

    private fun MalPublicAnimeNode.toMetaDetails(
        canonicalId: String,
        requestedType: String,
        enrichment: AnimeEnrichment,
    ): MetaDetails {
        val type = contentType(requestedType)
        val malPoster = mainPicture?.large ?: mainPicture?.medium
        // TVDB's series poster is the show's, so it is only correct for a first season or a film;
        // for a sequel MAL's own cover is the more accurate picture of this entry.
        val poster = enrichment.tvdbArtwork.posterUrl
            ?.takeIf { enrichment.mappedSeason == null || enrichment.mappedSeason <= 1 }
            ?: enrichment.aniZip?.artworkUrl("Poster")
            ?: malPoster
        val episodes = if (type == "movie") {
            emptyList()
        } else {
            buildEnrichedEpisodes(
                videoIdPrefix = canonicalId,
                episodeCount = episodeCount,
                enrichment = enrichment,
                seriesPoster = poster,
                fallbackTitle = { number -> "Episode $number" },
            )
        }
        return MetaDetails(
            id = canonicalId,
            type = type,
            name = alternativeTitles?.en?.takeIf(String::isNotBlank) ?: title,
            poster = poster,
            // MAL's `pictures` are alternate posters, not backdrops, so a 1920x1080 TVDB fanart is a
            // large upgrade behind the details header.
            background = enrichment.tvdbArtwork.backgroundUrl
                ?: enrichment.aniZip?.fanartUrl()
                ?: pictures.firstOrNull()?.large,
            logo = enrichment.tvdbArtwork.clearLogoUrl ?: enrichment.aniZip?.clearLogoUrl(),
            description = synopsis,
            releaseInfo = season?.year?.toString() ?: startDate?.take(4),
            lastAirDate = endDate,
            status = status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
            imdbRating = score?.takeIf { it > 0 }?.toString(),
            runtime = episodeDurationSeconds?.takeIf { it > 0 }?.div(60)?.let(::formatRuntimeFromMinutes),
            genres = genres.map { it.name },
            productionCompanies = studios.map { MetaCompany(it.name) },
            links = listOf(MetaLink("MyAnimeList", "external", "https://myanimelist.net/anime/$id")),
            videos = episodes,
        )
    }

    /**
     * Serves the two rows Kitsu does better, or null to let MAL handle the request.
     *
     * Kitsu's trending endpoint has no offset, so pages beyond the first defer to MAL instead of
     * repeating page one.
     */
    private suspend fun kitsuCatalog(
        catalogId: String,
        contentType: String,
        limit: Int,
        offset: Int,
        page: Int,
        forceRefresh: Boolean,
    ): CatalogPage? {
        val items = when (catalogId) {
            "trending" -> if (page > 1) return null else {
                KitsuClient.trendingAnime(contentType, limit, forceRefresh)
            }
            "recently-released" -> KitsuClient.recentlyReleasedAnime(
                contentType = contentType,
                limit = limit,
                offset = offset,
                forceRefresh = forceRefresh,
            )
            else -> return null
        }
        if (items.isEmpty()) return null

        val previews = coroutineScope {
            items.map { anime -> async { anime.toPreview(contentType, forceRefresh) } }
                .awaitAll()
                .filterNotNull()
        }
        if (previews.isEmpty()) return null
        return CatalogPage(
            items = previews,
            rawItemCount = items.size,
            // Trending is single-page; recency pages until Kitsu stops filling a full page.
            nextSkip = (page + 1).takeIf { catalogId != "trending" && items.size >= limit },
        )
    }

    private suspend fun KitsuAnime.toPreview(requestedType: String, forceRefresh: Boolean): MetaPreview? {
        val poster = posterUrl ?: return null
        return MetaPreview(
            // Canonicalised through ani.zip so a Kitsu-sourced card opens the same details entry as
            // the AniList- or MAL-sourced one, and watch state carries over.
            id = kitsuCanonicalId(id, forceRefresh),
            type = if (subtype.equals("movie", ignoreCase = true)) "movie" else requestedType,
            name = title,
            poster = poster,
            posterShape = PosterShape.Poster,
            description = synopsis,
            releaseInfo = startDate?.take(4),
            rawReleaseDate = startDate,
            popularity = userCount?.toDouble(),
            imdbRating = rating?.let { value -> ((value * 10).toInt() / 10.0).toString() },
        )
    }

    private suspend fun kitsuCanonicalId(kitsuId: String, forceRefresh: Boolean): String {
        val numericId = kitsuId.toIntOrNull() ?: return "kitsu:$kitsuId"
        val mapping = AniZipClient.mappingsByKitsuId(numericId, forceRefresh)
        val anilistId = mapping?.mappings?.get("anilist_id")?.toString()?.trim('"')?.toIntOrNull()
        if (anilistId != null) return "anilist:$anilistId"
        val malId = mapping?.malId()?.toIntOrNull()
        return if (malId != null) "mal:$malId" else "kitsu:$kitsuId"
    }

    private suspend fun canonicalId(malId: Int, forceRefresh: Boolean): String {
        val mapping = AniZipClient.mappingsByMalId(malId, forceRefresh)
        val anilistId = mapping?.mappings?.get("anilist_id")?.toString()?.trim('"')?.toIntOrNull()
        return anilistId?.let { "anilist:$it" } ?: "mal:$malId"
    }

    private suspend fun resolveMalId(id: String, forceRefresh: Boolean): Int? = when {
        id.startsWith("mal:", true) -> id.substringAfter(':').substringBefore(':').toIntOrNull()
        id.startsWith("anilist:", true) -> id.substringAfter(':').substringBefore(':').toIntOrNull()
            ?.let { AniZipClient.mappings(it, forceRefresh)?.malId()?.toIntOrNull() }
        else -> null
    }

    private fun MalPublicAnimeNode.contentType(requestedType: String): String =
        if (mediaType.equals("movie", true)) "movie" else requestedType.takeIf { it == "movie" } ?: "series"

    private fun Int?.orZero(): Int = this?.takeIf { it > 0 } ?: 0

    private fun catalogContentType(id: String): String = if (id == "movies") "movie" else "series"

    private fun catalogRequest(id: String): Pair<String, Map<String, String>> = when (id) {
        "recently-released" -> "anime/season/${currentYear()}/${currentSeason()}" to mapOf("sort" to "anime_start_date")
        "upcoming" -> "anime/season/${nextSeason().first}/${nextSeason().second}" to mapOf("sort" to "anime_num_list_users")
        "trending" -> "anime/ranking" to mapOf("ranking_type" to "airing")
        "top-rated" -> "anime/ranking" to mapOf("ranking_type" to "all")
        "movies" -> "anime/ranking" to mapOf("ranking_type" to "movie")
        else -> "anime/ranking" to mapOf("ranking_type" to "bypopularity")
    }

    private fun currentYear(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    private fun currentSeason(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) {
        in 1..3 -> "winter"; in 4..6 -> "spring"; in 7..9 -> "summer"; else -> "fall"
    }

    private fun nextSeason(): Pair<Int, String> = when (currentSeason()) {
        "winter" -> currentYear() to "spring"; "spring" -> currentYear() to "summer"
        "summer" -> currentYear() to "fall"; else -> currentYear() + 1 to "winter"
    }

    private const val LIST_FIELDS = "id,title,main_picture,alternative_titles,start_date," +
        "synopsis,mean,popularity,num_list_users,media_type,genres"
}
