package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniZipClient
import com.nuvio.app.core.anilist.FanartArtwork
import com.nuvio.app.core.anilist.FanartClient
import com.nuvio.app.core.anilist.TvdbArtwork
import com.nuvio.app.core.anilist.TvdbClient
import com.nuvio.app.core.anilist.fanartUrl
import com.nuvio.app.core.anilist.hasArtwork
import com.nuvio.app.core.anilist.mappedSeasonNumber
import com.nuvio.app.core.anilist.logoUrl
import com.nuvio.app.core.anilist.tmdbId
import com.nuvio.app.core.anilist.tvdbId
import com.nuvio.app.core.anivio.AnivioClient
import com.nuvio.app.core.anivio.backgroundUrl
import com.nuvio.app.core.anivio.logoUrl
import com.nuvio.app.core.anivio.posterUrl
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.tmdb.TmdbArtwork
import com.nuvio.app.features.tmdb.TmdbArtworkSource
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Upgrades hero artwork for AniList items.
 *
 * AniList only offers a portrait cover plus an often-missing `bannerImage`, so the mobile hero
 * needs a sharper portrait poster and a transparent title logo:
 *
 * - **poster**: TVDB's 680x1000 key art (series or movie) -> Anivio -> TMDB -> AniList.
 * - **logo**: TVDB clear logo -> fanart.tv -> ani.zip -> TMDB -> AniList -> Anivio.
 * - **backdrop**: TVDB background -> TMDB -> fanart.tv -> ani.zip -> AniList -> Anivio.
 *
 * TVDB is tried first even when `api.ani.zip` has no mapping: the id comes from ani.zip when it
 * does, otherwise from a TVDB title search. `HomeHeroSection` already renders `MetaPreview.logo`
 * in place of the title text and falls back to text when it is absent, so nothing in the UI changes.
 */
object AniListHeroArtwork {
    private const val RETRY_AFTER_MS = 30L * 60 * 1000
    private const val MAX_ATTEMPT_ENTRIES = 320

    /**
     * Enriched copies keyed by [MetaPreview.stableKey]. Held in an atomic reference rather than
     * behind a mutex so the home publish path — which is not suspending — can read it directly.
     */
    private val cacheRef = atomic<Map<String, MetaPreview>>(emptyMap())

    /**
     * When each unresolved title was last attempted. Keeping misses out of [cacheRef] stops them
     * being written to disk, while this timestamp stops a failed lookup re-firing on every publish.
     */
    private val attemptsRef = atomic<Map<String, Long>>(emptyMap())

    fun snapshot(): Map<String, MetaPreview> = cacheRef.value

    /**
     * Seeds the cache with artwork resolved in an earlier session, from `HomeCatalogCache`.
     * Anything resolved in this process wins, so hydrating can only fill gaps.
     */
    fun hydrate(entries: Map<String, MetaPreview>) {
        if (entries.isEmpty()) return
        cacheRef.update { current -> entries + current }
        attemptsRef.update { current -> current - entries.keys }
    }

    fun hasUnresolved(items: List<MetaPreview>): Boolean {
        val cache = cacheRef.value
        val attempts = attemptsRef.value
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        return items.any { item ->
            val key = item.stableKey()
            isAniListId(item.id) &&
                (cache[key] == null || needsArtworkRefresh(cache.getValue(key))) &&
                now - (attempts[key] ?: 0L) > RETRY_AFTER_MS
        }
    }

    /**
     * Resolves artwork for every AniList item not already cached. A result that changed is cached;
     * one that did not is only timestamped, so the next app launch (or the retry window) tries
     * again instead of keeping the low-resolution AniList cover for a week.
     */
    suspend fun resolve(items: List<MetaPreview>) {
        val cache = cacheRef.value
        val attempts = attemptsRef.value
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        val pending = items.filter { item ->
            val key = item.stableKey()
            isAniListId(item.id) &&
                (cache[key] == null || needsArtworkRefresh(cache.getValue(key))) &&
                now - (attempts[key] ?: 0L) > RETRY_AFTER_MS
        }
        if (pending.isEmpty()) return

        val results = coroutineScope {
            pending.map { item -> async { item to enrich(item) } }.awaitAll()
        }
        val resolved = results.filter { (item, enriched) -> enriched != item }
        val unresolved = results.filter { (item, enriched) -> enriched == item }

        if (resolved.isNotEmpty()) {
            cacheRef.value = cacheRef.value + resolved.associate { (item, enriched) ->
                item.stableKey() to enriched
            }
        }
        if (unresolved.isNotEmpty()) {
            attemptsRef.update { current ->
                val next = current + unresolved.associate { (item, _) -> item.stableKey() to now }
                if (next.size <= MAX_ATTEMPT_ENTRIES) {
                    next
                } else {
                    next.entries.toList()
                        .takeLast(MAX_ATTEMPT_ENTRIES)
                        .associate { it.key to it.value }
                }
            }
        }
    }

    /** Drops resolved and attempted entries, e.g. after artwork settings change. */
    fun invalidate() {
        cacheRef.value = emptyMap()
        attemptsRef.value = emptyMap()
    }

    private suspend fun enrich(item: MetaPreview): MetaPreview {
        val mediaId = parseAniListMediaId(item.id) ?: return item

        return try {
            TrackingSettingsRepository.ensureLoaded()
            val aniZip = AniZipClient.mappings(mediaId)
            val anivioMedia = if (TrackingSettingsRepository.uiState.value.anivioAssetsEnabled) {
                AnivioClient.mediaByAniListIdOrNull(mediaId)
            } else {
                null
            }
            val isMovie = item.type.equals("movie", ignoreCase = true)
            val tmdbId = aniZip?.tmdbId()
            val mappedSeason = aniZip?.mappedSeasonNumber()

            // TVDB first: use ani.zip's id when it has one, otherwise search TVDB by title. Movies
            // try the movie index before the series index because anime films are split across both.
            val tvdbId = aniZip?.tvdbId() ?: if (isMovie) {
                TvdbClient.searchMovieId(item.name) ?: TvdbClient.searchSeriesId(item.name)
            } else {
                TvdbClient.searchSeriesId(item.name)
            }

            val (tmdbArtwork, fanartArtwork, tvdbArtwork) = coroutineScope {
                val tmdb = async {
                    if (tmdbId == null) TmdbArtwork() else TmdbArtworkSource.artwork(tmdbId, item.type)
                }
                val fanart = async {
                    if (isMovie) {
                        tmdbId?.let { FanartClient.movieArtwork(it) } ?: FanartArtwork()
                    } else {
                        tvdbId?.let { FanartClient.tvArtwork(it) } ?: FanartArtwork()
                    }
                }
                val tvdb = async {
                    when {
                        tvdbId == null -> TvdbArtwork()
                        isMovie -> TvdbClient.movieArtwork(tvdbId)
                            .takeIf { it.hasArtwork() }
                            ?: TvdbClient.seriesArtwork(tvdbId)
                        else -> TvdbClient.seriesArtwork(tvdbId)
                    }
                }
                Triple(tmdb.await(), fanart.await(), tvdb.await())
            }

            // A sequel's season poster is season-specific; TVDB's series poster is the show's, so it
            // only applies to a first season or a film.
            val seasonPoster = if (!isMovie && tvdbId != null && mappedSeason != null && mappedSeason > 1) {
                TvdbClient.seasonPosterUrl(seriesId = tvdbId, seasonNumber = mappedSeason)
            } else {
                null
            }
            val seriesPosterApplies = isMovie || mappedSeason == null || mappedSeason <= 1

            item.copy(
                // TVDB's 680x1000 portrait is the point of this path; AniList's ~460px cover is the
                // last resort. A sequel's season poster is more specific, so it wins for later seasons.
                poster = seasonPoster
                    ?: tvdbArtwork.posterUrl?.takeIf { seriesPosterApplies }
                    ?: anivioMedia?.posterUrl
                    ?: tmdbArtwork.posterUrl
                    ?: item.poster,
                banner = tvdbArtwork.backgroundUrl
                    ?: tmdbArtwork.backdropUrl
                    ?: fanartArtwork.backgroundUrl
                    ?: aniZip?.fanartUrl()
                    ?: item.banner
                    ?: anivioMedia?.backgroundUrl,
                logo = tvdbArtwork.clearLogoUrl
                    ?: fanartArtwork.logoUrl
                    ?: aniZip?.logoUrl()
                    ?: tmdbArtwork.logoUrl
                    ?: item.logo
                    ?: anivioMedia?.logoUrl,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Leave the item untouched; resolve() records the attempt so it retries later.
            item
        }
    }

    /** Cached hero entries created before an artwork source was available must be retried. */
    private fun needsArtworkRefresh(item: MetaPreview): Boolean =
        item.banner.isNullOrBlank() || item.logo.isNullOrBlank()
}
