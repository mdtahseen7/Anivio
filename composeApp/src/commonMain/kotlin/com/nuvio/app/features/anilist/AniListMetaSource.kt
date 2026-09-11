package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.ANILIST_MEDIA_DETAIL_FIELDS
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListMediaDetailDiskCache
import com.nuvio.app.core.anilist.AniListRateLimitedException
import com.nuvio.app.core.anilist.AniListUnavailableException
import com.nuvio.app.core.anilist.AniListMediaDetail
import com.nuvio.app.core.anilist.AniListStaffEdge
import com.nuvio.app.core.anilist.AniZipClient
import com.nuvio.app.core.anilist.AniZipResponse
import com.nuvio.app.core.anilist.clearLogoUrl
import com.nuvio.app.core.anilist.fanartUrl
import com.nuvio.app.core.anilist.imdbId
import com.nuvio.app.core.anilist.KitsuEpisode
import com.nuvio.app.core.anilist.TvdbArtwork
import com.nuvio.app.core.anilist.TvdbClient
import com.nuvio.app.core.anilist.TvdbEpisode
import com.nuvio.app.core.anilist.tvdbId
import com.nuvio.app.core.anilist.kitsuId
import com.nuvio.app.core.anilist.mappedSeasonNumber
import com.nuvio.app.core.anilist.stripAniListMarkup
import com.nuvio.app.core.anilist.tmdbId
import com.nuvio.app.core.anilist.toMetaPreview
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.core.anilist.airingScheduleByEpisode
import com.nuvio.app.features.anime.AnimeEnrichment
import com.nuvio.app.features.anime.buildEnrichedEpisodes
import com.nuvio.app.features.anime.loadAnimeEnrichment
import com.nuvio.app.features.details.MetaCompany
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaLink
import com.nuvio.app.features.details.MetaPerson
import com.nuvio.app.features.details.MoreLikeThisSource
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.details.MetaTrailer
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.formatRuntimeFromMinutes
import com.nuvio.app.features.tmdb.TmdbArtworkSource
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.tmdb.TmdbSettings
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_episode_fallback_title
import org.jetbrains.compose.resources.getString

const val ANILIST_ID_PREFIX = "anilist:"

/** AniList models each season as its own entry, so episodes are flat under a single season. */
const val ANILIST_SEASON = 1

fun isAniListId(id: String): Boolean =
    id.trim().startsWith(ANILIST_ID_PREFIX, ignoreCase = true)

/** Both `anilist:21` and the episode form `anilist:21:1:5` resolve to media 21. */
fun parseAniListMediaId(id: String): Int? {
    val trimmed = id.trim()
    if (!isAniListId(trimmed)) return null
    return trimmed.substring(ANILIST_ID_PREFIX.length)
        .substringBefore(':')
        .toIntOrNull()
        ?.takeIf { it > 0 }
}

data class AniListMetaResult(
    val meta: MetaDetails,
    /** ani.zip's IMDb id, for the MdbList and Trakt lookups that key on one. */
    val externalFallbackId: String?,
)

/**
 * Builds the details page for an `anilist:` id.
 *
 * AniList supplies the core record (synopsis, score, genres, studios, characters, staff);
 * `api.ani.zip` supplies external ids, TVDB series artwork and per-episode titles, overviews,
 * stills, air dates and runtimes; TMDB — when the user has configured a key in Settings — is the
 * last layer, reached through the TMDB id ani.zip resolved for us.
 */
object AniListMetaSource {
    private const val TMDB_ENRICH_TIMEOUT_MS = 6_000L

    /** Matches what the TMDB path used to return, so the rail's length does not visibly change. */
    private const val MAX_MORE_LIKE_THIS = 12

    /** AniList `format` values that are not watchable. */
    private val MANGA_FORMATS = setOf("MANGA", "NOVEL", "ONE_SHOT")

    /**
     * How long the fully enriched result gets before a partial paint is published instead.
     *
     * Long enough that a warm cache always wins the race (disk reads are sub-millisecond here),
     * short enough to be under the threshold where a blank screen starts to feel broken.
     */
    private const val PARTIAL_PAINT_BUDGET_MS = 220L

    private val log = Logger.withTag("AniListMetaSource")

    /**
     * Background refreshes for cache entries that are usable but past their window. Deliberately
     * detached from the caller's scope: leaving the details screen must not cancel the refresh, and
     * the refresh must never delay the screen.
     */
    private val revalidationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val DIRECTOR_ROLE_HINTS = listOf("director")
    private val WRITER_ROLE_HINTS = listOf("original creator", "series composition", "script", "screenplay", "story")

    /**
     * AniList's staff list mixes principal credits with dub and per-episode ones. Even sorted by
     * relevance, "Director" matches "ADR Director (French)", "Episode Director (ep 15)" and
     * "Assistant Director", so those have to be filtered out explicitly.
     */
    private val NON_PRINCIPAL_ROLE_HINTS = listOf(
        "adr",
        "animation director",
        "art director",
        "assistant",
        "episode",
        "sound director",
        "sub ",
        "unit",
        // Episode-scoped credits, e.g. "Script (eps 4, 13)".
        "(ep",
    )

    /**
     * @param onPartialMeta invoked with a renderable-but-unenriched [MetaDetails] when the full
     * result is taking long enough that showing something beats showing a spinner. Never called when
     * the enrichment resolves from cache.
     */
    suspend fun fetchMeta(
        id: String,
        type: String,
        forceRefresh: Boolean = false,
        onPartialMeta: ((MetaDetails) -> Unit)? = null,
    ): AniListMetaResult? = withContext(Dispatchers.Default) {
        val mediaId = parseAniListMediaId(id) ?: return@withContext null

        val (detail, aniZip) = coroutineScope {
            val mediaDeferred = async { fetchMediaDetail(mediaId, forceRefresh) }
            // Best-effort: a null here just means fewer episode stills and no TMDB hop.
            val aniZipDeferred = async { AniZipClient.mappings(mediaId, forceRefresh) }
            mediaDeferred.await() to aniZipDeferred.await()
        }

        val media = detail ?: return@withContext null
        val isMovie = type.equals("movie", ignoreCase = true)
        val mappedSeason = aniZip?.mappedSeasonNumber()
        val externalFallbackId = aniZip?.imdbId()

        coroutineScope {
            // Everything past this point needs ids from ani.zip, so it cannot start any earlier —
            // but it is all optional polish. Shared with the MAL provider so the two cannot drift.
            val enrichedDeferred = async {
                val enrichment = loadAnimeEnrichment(
                    aniZip = aniZip,
                    isMovie = isMovie,
                    expectedEpisodeCount = media.episodes,
                ).copy(airingScheduleByEpisode = media.airingScheduleByEpisode())
                val base = media.toMetaDetails(
                    itemId = id,
                    itemType = type,
                    aniZip = aniZip,
                    kitsuEpisodes = enrichment.kitsuEpisodes,
                    tvdbEpisodes = enrichment.tvdbEpisodes,
                    tvdbArtwork = enrichment.tvdbArtwork,
                    mappedSeason = mappedSeason,
                )
                enrichWithTmdb(meta = base, aniZip = aniZip)
            }

            // Raced rather than simply published early. Wave 1 alone already renders a complete-
            // looking page — AniList's record plus ani.zip's episode list — so waiting on TVDB,
            // Kitsu and TMDB before showing anything is what made a cold open feel slow. But when
            // those are all cached the enrichment lands in microseconds, and emitting a partial
            // first would make every revisit visibly swap its artwork. So: give the full result a
            // short head start, and only fall back to a partial paint if it does not make it.
            val settled = withTimeoutOrNull(PARTIAL_PAINT_BUDGET_MS) { enrichedDeferred.await() }
            if (settled != null) {
                return@coroutineScope AniListMetaResult(settled, externalFallbackId)
            }

            onPartialMeta?.invoke(
                media.toMetaDetails(
                    itemId = id,
                    itemType = type,
                    aniZip = aniZip,
                    kitsuEpisodes = emptyMap(),
                    tvdbEpisodes = emptyMap(),
                    tvdbArtwork = TvdbArtwork(),
                    mappedSeason = mappedSeason,
                ),
            )

            AniListMetaResult(
                meta = enrichedDeferred.await(),
                externalFallbackId = externalFallbackId,
            )
        }
    }

    /**
     * The one call here that spends AniList's 30-requests-per-minute budget, so it is read from disk
     * whenever a fresh-enough copy exists.
     *
     * When the network attempt fails for a reason that is about capacity rather than the record —
     * a 429, or AniList being down — a stale cached copy is served instead of failing. That is the
     * difference between a details screen that renders slightly old scores and one that shows an
     * error and has to be retried.
     */
    private suspend fun fetchMediaDetail(mediaId: Int, forceRefresh: Boolean): AniListMediaDetail? {
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        if (!forceRefresh) {
            val cached = AniListMediaDetailDiskCache.peek(mediaId, now)
            if (cached != null) {
                if (cached.isFresh) return cached.detail
                // Past its window but still usable. Render it now and revalidate behind the screen:
                // for an airing show the only fields that moved are the score and the next air date,
                // so blocking the whole details page on that trade is the wrong one — especially
                // when the request may be the one that trips the rate limit.
                revalidationScope.launch {
                    try {
                        requestMediaDetail(mediaId, forceRefresh = true)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        // Nothing to report: the screen already rendered from cache.
                        log.d(error) { "Background revalidation of Media $mediaId failed" }
                    }
                }
                return cached.detail
            }
        }

        return try {
            requestMediaDetail(mediaId, forceRefresh)
        } catch (error: AniListRateLimitedException) {
            AniListMediaDetailDiskCache.getStale(mediaId)?.also {
                log.i { "AniList throttled Media $mediaId; serving stale cached detail" }
            }
        } catch (error: AniListUnavailableException) {
            AniListMediaDetailDiskCache.getStale(mediaId)
                ?.also { log.i { "AniList unavailable; serving stale cached detail for $mediaId" } }
                ?: throw error
        } catch (cancellation: CancellationException) {
            // Navigating away cancels this; swallowing it would leave the caller's scope confused.
            throw cancellation
        } catch (error: Throwable) {
            log.w(error) { "AniList Media $mediaId lookup failed" }
            AniListMediaDetailDiskCache.getStale(mediaId)
        }
    }

    /** The network half of [fetchMediaDetail]: fetch, decode, persist. Throws on failure. */
    private suspend fun requestMediaDetail(mediaId: Int, forceRefresh: Boolean): AniListMediaDetail? {
        val data = AniListClient.query(
            query = "query { Media(id: $mediaId, type: ANIME) { $ANILIST_MEDIA_DETAIL_FIELDS } }",
            forceRefresh = forceRefresh,
        )
        val mediaObject = data["Media"] as? JsonObject ?: return null
        return AniListClient.json
            .decodeFromJsonElement(AniListMediaDetail.serializer(), mediaObject)
            .also { detail ->
                AniListMediaDetailDiskCache.put(
                    mediaId,
                    detail,
                    EpisodeReleaseDatePlatform.nowEpochMs(),
                )
            }
    }

    private fun AniListMediaDetail.toMetaDetails(
        itemId: String,
        itemType: String,
        aniZip: AniZipResponse?,
        kitsuEpisodes: Map<Int, KitsuEpisode>,
        tvdbEpisodes: Map<Int, TvdbEpisode>,
        tvdbArtwork: TvdbArtwork,
        mappedSeason: Int?,
    ): MetaDetails {
        val staffEdges = staff?.edges.orEmpty()
        val animationStudios = studios?.nodes.orEmpty()
            .sortedByDescending { it.isAnimationStudio }
            .map { MetaCompany(name = it.name) }
        val recommendedTitles = aniListRecommendations()

        return MetaDetails(
            id = itemId,
            type = itemType,
            name = preferredTitle() ?: itemId,
            // TVDB's series poster is the show's, so it is only right for a first season or a film;
            // sequels get their season poster in enrichWithTmdb instead.
            poster = tvdbArtwork.posterUrl?.takeIf { mappedSeason == null || mappedSeason <= 1 }
                ?: coverImage?.extraLarge?.takeIf { it.isNotBlank() }
                ?: coverImage?.large?.takeIf { it.isNotBlank() },
            // A 1920x1080 TVDB background beats AniList's thin banner behind the details header,
            // and ani.zip's single fanart pick is the fallback when TVDB has none.
            background = tvdbArtwork.backgroundUrl
                ?: bannerImage?.takeIf { it.isNotBlank() }
                ?: aniZip?.fanartUrl(),
            logo = tvdbArtwork.clearLogoUrl ?: aniZip?.clearLogoUrl(),
            description = description?.stripAniListMarkup(),
            releaseInfo = (seasonYear ?: startDate?.year)?.toString(),
            lastAirDate = endDate?.toIsoDateOrNull(),
            status = status?.humanizeAniListEnum(),
            imdbRating = averageScore?.takeIf { it > 0 }?.let { (it / 10.0).toString() },
            runtime = duration?.takeIf { it > 0 }?.let(::formatRuntimeFromMinutes),
            genres = genres,
            director = staffEdges.namesForRoles(DIRECTOR_ROLE_HINTS),
            writer = staffEdges.namesForRoles(WRITER_ROLE_HINTS),
            cast = characters?.edges.orEmpty().mapNotNull { edge ->
                val characterName = edge.node?.name?.full?.takeIf { it.isNotBlank() }
                val actor = edge.voiceActors.firstOrNull()
                val actorName = actor?.name?.full?.takeIf { it.isNotBlank() }
                when {
                    // Mirror the app's TMDB-sourced casts: the person on top, their role beneath.
                    actorName != null -> MetaPerson(
                        name = actorName,
                        role = characterName,
                        photo = actor.image?.large ?: actor.image?.medium,
                    )

                    characterName != null -> MetaPerson(
                        name = characterName,
                        photo = edge.node.image?.large ?: edge.node.image?.medium,
                    )

                    else -> null
                }
            },
            productionCompanies = animationStudios,
            country = countryOfOrigin?.takeIf { it.isNotBlank() },
            moreLikeThis = recommendedTitles,
            moreLikeThisSource = MoreLikeThisSource.ANILIST.takeIf { recommendedTitles.isNotEmpty() },
            hasScheduledVideos = nextAiringEpisode != null,
            trailers = trailerOrNull(),
            links = externalLinks.mapNotNull { link ->
                val url = link.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = link.site?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                MetaLink(name = name, category = "external", url = url)
            },
            videos = if (itemType.equals("movie", ignoreCase = true)) {
                emptyList()
            } else {
                buildVideos(
                    mediaId = id,
                    episodeCount = episodes,
                    aniZip = aniZip,
                    kitsuEpisodes = kitsuEpisodes,
                    tvdbEpisodes = tvdbEpisodes,
                    airingScheduleByEpisode = airingScheduleByEpisode(),
                    seriesPoster = coverImage?.extraLarge ?: coverImage?.large,
                )
            },
        )
    }

    /**
     * "More like this" from AniList's own community recommendations.
     *
     * These used to come from TMDB, which meant the rail was filled with `tmdb:` ids. Nothing in an
     * AniList-first app can open one — there is no `tmdb:` branch in the details resolver ahead of
     * the addon path — so tapping a card led nowhere. AniList's recommendations are anime-aware,
     * arrive inside the `Media` query already being made, and carry canonical `anilist:` ids that
     * every screen in the app already handles.
     *
     * Negative ratings are dropped: the field is upvotes minus downvotes, so below zero means the
     * community actively rejected the suggestion. Adult titles are dropped too, matching the rest of
     * the catalog surfaces.
     */
    private fun AniListMediaDetail.aniListRecommendations(): List<MetaPreview> =
        recommendations?.nodes.orEmpty()
            .filter { (it.rating ?: 0) >= 0 }
            .mapNotNull { it.mediaRecommendation }
            .filterNot { it.isAdult }
            // AniList's recommendation graph spans both anime and manga. A manga entry would map to
            // `series` and produce a card with no episodes and nothing to play behind it.
            .filterNot { it.format?.uppercase() in MANGA_FORMATS }
            .distinctBy { it.id }
            .mapNotNull { it.toMetaPreview() }
            .take(MAX_MORE_LIKE_THIS)

    /**
     * One [MetaVideo] per AniList episode, enriched from ani.zip where it has a matching entry.
     * Walks `1..episodeCount` rather than ani.zip's key set, because ani.zip also carries specials
     * (under `S`-prefixed keys) and, for long-running shows, episodes beyond this AniList entry.
     */
    private fun buildVideos(
        mediaId: Int,
        episodeCount: Int?,
        aniZip: AniZipResponse?,
        kitsuEpisodes: Map<Int, KitsuEpisode>,
        tvdbEpisodes: Map<Int, TvdbEpisode>,
        airingScheduleByEpisode: Map<Int, Long>,
        seriesPoster: String?,
    ): List<MetaVideo> = buildEnrichedEpisodes(
        videoIdPrefix = "$ANILIST_ID_PREFIX$mediaId",
        episodeCount = episodeCount,
        enrichment = AnimeEnrichment(
            aniZip = aniZip,
            kitsuEpisodes = kitsuEpisodes,
            tvdbEpisodes = tvdbEpisodes,
            airingScheduleByEpisode = airingScheduleByEpisode,
        ),
        seriesPoster = seriesPoster,
        fallbackTitle = ::fallbackEpisodeTitle,
    )

    private suspend fun enrichWithTmdb(meta: MetaDetails, aniZip: AniZipResponse?): MetaDetails {
        val tmdbId = aniZip?.tmdbId() ?: return meta
        TmdbSettingsRepository.ensureLoaded()
        val settings = TmdbSettingsRepository.snapshot()
        // A key is the only hard requirement: "More like this" is always fetched, so switching the
        // enrichment toggle off narrows TMDB down to recommendations rather than turning it off.
        if (!settings.hasApiKey) return meta

        // AniList gives every anime season its own entry; TMDB models the whole run as one show, so
        // Re:ZERO season 4 and season 1 share themoviedb_id 65942. Anything season-shaped that TMDB
        // returns is therefore season 1's — episode lists, stills, air dates and show artwork alike.
        val mappedSeason = aniZip.mappedSeasonNumber()
        val isLaterSeason = mappedSeason != null && mappedSeason > 1

        // AniList already supplied anime-aware recommendations with openable ids. Asking TMDB for its
        // own set would spend a request to produce `tmdb:` cards that then have to be discarded.
        val hasAniListRecommendations = meta.moreLikeThisSource == MoreLikeThisSource.ANILIST &&
            meta.moreLikeThis.isNotEmpty()

        if (!settings.enabled && hasAniListRecommendations) return meta

        val enriched = withTimeoutOrNull(TMDB_ENRICH_TIMEOUT_MS) {
            runCatching {
                TmdbMetadataService.enrichMeta(
                    meta = meta,
                    // ani.zip already resolved the TMDB id, so this skips the IMDb->TMDB round trip
                    // that TmdbService.ensureTmdbId would otherwise make.
                    fallbackItemId = "tmdb:$tmdbId",
                    settings = if (!settings.enabled) recommendationsOnly(settings) else settings.copy(
                        useMoreLikeThis = settings.useMoreLikeThis && !hasAniListRecommendations,
                        // TMDB's trailer lookup is the most expensive thing in this whole enrichment:
                        // `/videos`, then a second `/tv/{id}` purely to count seasons, then one
                        // `/season/{n}/videos` per season — seven requests for a four-season show.
                        // AniList already handed us the show's trailer, so only pay that when it did
                        // not.
                        useTrailers = settings.useTrailers && meta.trailers.isEmpty(),
                        // ani.zip is keyed on the AniList id, so its episodes are season-correct and
                        // already populated. TMDB would be asked for season 1 regardless, because
                        // MetaVideo.season stays 1 to keep episode ids stable for stream lookups.
                        useEpisodes = false,
                        useSeasonPosters = false,
                        useReleaseDates = false,
                        // Show-level artwork is fine for a first season or a film, wrong for a sequel.
                        useArtwork = settings.useArtwork && !isLaterSeason,
                    ),
                )
            }.onFailure { error ->
                log.w(error) { "TMDB enrichment failed for ${meta.id}" }
            }.getOrNull()
        } ?: return meta

        // TMDB overwrites two fields outright that AniList should own here: the title (so the
        // details page matches the row the user tapped) and the production companies (TMDB lists
        // anime licensors and distributors; AniList names the actual animation studio).
        val merged = enriched.copy(
            name = meta.name,
            productionCompanies = mergeCompanies(meta.productionCompanies, enriched.productionCompanies),
            // Belt and braces alongside the disabled toggle above: whatever TMDB returned, the
            // AniList rail is the one with ids this app can open.
            moreLikeThis = if (hasAniListRecommendations) meta.moreLikeThis else enriched.moreLikeThis,
            moreLikeThisSource = if (hasAniListRecommendations) {
                MoreLikeThisSource.ANILIST
            } else {
                enriched.moreLikeThisSource
            },
        )

        if (!isLaterSeason || !settings.enabled) return merged

        // Sequels still deserve a high-resolution poster, and this is the other thing TVDB is best
        // at: the season's own key art, filtered to Aired Order. TMDB's season endpoint is the
        // fallback, then AniList's ~460px cover.
        val seasonPoster = withTimeoutOrNull(TMDB_ENRICH_TIMEOUT_MS) {
            aniZip.tvdbId()?.let { seriesId ->
                TvdbClient.seasonPosterUrl(seriesId = seriesId, seasonNumber = mappedSeason)
            } ?: TmdbArtworkSource.seasonPosterUrl(
                tmdbId = tmdbId,
                seasonNumber = mappedSeason,
                language = settings.language,
            )
        }
        return if (seasonPoster == null) merged else merged.copy(poster = seasonPoster)
    }

    /**
     * Everything off except recommendations, for when the user has TMDB enrichment switched off but
     * still wants a "More like this" row.
     */
    private fun recommendationsOnly(settings: TmdbSettings): TmdbSettings = TmdbSettings(
        enabled = true,
        apiKey = settings.apiKey,
        language = settings.language,
        useTrailers = false,
        useArtwork = false,
        useBasicInfo = false,
        useDetails = false,
        useReleaseDates = false,
        useCredits = false,
        useProductions = false,
        useNetworks = false,
        useEpisodes = false,
        useSeasonPosters = false,
        useMoreLikeThis = true,
        useCollections = false,
    )

    /** AniList studios first, then any TMDB company we did not already have — TMDB carries logos. */
    private fun mergeCompanies(
        preferred: List<MetaCompany>,
        extra: List<MetaCompany>,
    ): List<MetaCompany> {
        if (preferred.isEmpty()) return extra
        val merged = linkedMapOf<String, MetaCompany>()
        preferred.forEach { company -> merged[company.name.lowercase()] = company }
        extra.forEach { company ->
            val key = company.name.lowercase()
            val existing = merged[key]
            merged[key] = existing?.copy(
                logo = existing.logo ?: company.logo,
                tmdbId = existing.tmdbId ?: company.tmdbId,
            ) ?: company
        }
        return merged.values.toList()
    }

    private fun AniListMediaDetail.preferredTitle(): String? =
        title?.english?.takeIf { it.isNotBlank() }
            ?: title?.romaji?.takeIf { it.isNotBlank() }
            ?: title?.nativeTitle?.takeIf { it.isNotBlank() }

    /** Only YouTube trailers — the player's trailer resolver does not handle other sites. */
    private fun AniListMediaDetail.trailerOrNull(): List<MetaTrailer> {
        val key = trailer?.id?.takeIf { it.isNotBlank() } ?: return emptyList()
        if (!trailer.site.equals("youtube", ignoreCase = true)) return emptyList()
        return listOf(
            MetaTrailer(
                id = key,
                key = key,
                name = preferredTitle().orEmpty(),
                site = "YouTube",
                official = true,
            ),
        )
    }

    private fun List<AniListStaffEdge>.namesForRoles(hints: List<String>): List<String> =
        filter { edge ->
            val role = edge.role?.lowercase() ?: return@filter false
            hints.any { role.contains(it) } && NON_PRINCIPAL_ROLE_HINTS.none { role.contains(it) }
        }
            .mapNotNull { it.node?.name?.full?.takeIf { name -> name.isNotBlank() } }
            .distinct()

    /** `NOT_YET_RELEASED` -> `Not Yet Released`. */
    private fun String.humanizeAniListEnum(): String =
        split('_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
            .ifBlank { this }

    private fun fallbackEpisodeTitle(number: Int): String =
        runBlocking { getString(Res.string.anilist_episode_fallback_title, number) }
}
