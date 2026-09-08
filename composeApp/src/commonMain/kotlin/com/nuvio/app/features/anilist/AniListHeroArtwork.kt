package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniZipClient
import com.nuvio.app.core.anilist.AniZipResponse
import com.nuvio.app.core.anilist.TvdbArtwork
import com.nuvio.app.core.anilist.TvdbClient
import com.nuvio.app.core.anilist.mappedSeasonNumber
import com.nuvio.app.core.anilist.FanartArtwork
import com.nuvio.app.core.anilist.FanartClient
import com.nuvio.app.core.anilist.clearLogoUrl
import com.nuvio.app.core.anilist.fanartUrl
import com.nuvio.app.core.anilist.tmdbId
import com.nuvio.app.core.anilist.tvdbId
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.stableKey
import com.nuvio.app.features.tmdb.TmdbArtwork
import com.nuvio.app.features.tmdb.TmdbArtworkSource
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Upgrades hero artwork for AniList items.
 *
 * AniList only offers a portrait cover plus an often-missing `bannerImage`, and a portrait poster
 * cropped to a full-bleed hero looks stretched. So for the handful of titles the hero actually
 * shows we resolve a real wide backdrop and a transparent title logo:
 *
 * - **backdrop**: TMDB `/images` (textless, needs the user's key) -> fanart.tv background ->
 *   ani.zip's TVDB fanart -> whatever AniList gave us.
 * - **logo**: fanart.tv (`hdtvlogo`/`hdmovielogo`, language-aware) -> ani.zip's TVDB Clearlogo ->
 *   TMDB logo. `HomeHeroSection` already renders `MetaPreview.logo` in place of the title text and
 *   falls back to text when it is absent or fails to load, so nothing in the UI changes.
 *
 * All three ids come from the single `api.ani.zip` call, so there is no title-search step.
 */
object AniListHeroArtwork {
    /**
     * Enriched copies keyed by [MetaPreview.stableKey]. Held in an atomic reference rather than
     * behind a mutex so the home publish path — which is not suspending — can read it directly.
     */
    private val cacheRef = atomic<Map<String, MetaPreview>>(emptyMap())

    fun snapshot(): Map<String, MetaPreview> = cacheRef.value

    /**
     * Seeds the cache with artwork resolved in an earlier session, from `HomeCatalogCache`.
     * Anything resolved in this process wins, so hydrating can only fill gaps.
     */
    fun hydrate(entries: Map<String, MetaPreview>) {
        if (entries.isEmpty()) return
        cacheRef.update { current -> entries + current }
    }

    fun hasUnresolved(items: List<MetaPreview>): Boolean {
        val cache = cacheRef.value
        return items.any { isAniListId(it.id) && it.stableKey() !in cache }
    }

    /**
     * Resolves artwork for every AniList item not already cached. Items with nothing resolvable are
     * cached unchanged, so a miss is not retried on every publish. Non-AniList items are ignored.
     */
    suspend fun resolve(items: List<MetaPreview>) {
        val cache = cacheRef.value
        val pending = items.filter { isAniListId(it.id) && it.stableKey() !in cache }
        if (pending.isEmpty()) return

        val resolved = coroutineScope {
            pending.map { item -> async { item.stableKey() to enrich(item) } }.awaitAll()
        }
        cacheRef.value = cacheRef.value + resolved.toMap()
    }

    private suspend fun enrich(item: MetaPreview): MetaPreview {
        val mediaId = parseAniListMediaId(item.id) ?: return item

        return try {
            val aniZip = AniZipClient.mappings(mediaId)
            val tmdbId = aniZip?.tmdbId()
            val tvdbId = aniZip?.tvdbId()
            val mappedSeason = aniZip?.mappedSeasonNumber()

            val (tmdbArtwork, fanartArtwork, tvdbArtwork) = coroutineScope {
                val tmdb = async {
                    if (tmdbId == null) TmdbArtwork() else TmdbArtworkSource.artwork(tmdbId, item.type)
                }
                val fanart = async { fanartFor(item, aniZip, tmdbId) }
                val tvdb = async { if (tvdbId == null) TvdbArtwork() else TvdbClient.seriesArtwork(tvdbId) }
                Triple(tmdb.await(), fanart.await(), tvdb.await())
            }

            // A sequel's season poster is season-specific; TVDB's series poster is the show's, so it
            // only applies to a first season or a film.
            val seasonPoster = if (tvdbId != null && mappedSeason != null && mappedSeason > 1) {
                TvdbClient.seasonPosterUrl(seriesId = tvdbId, seasonNumber = mappedSeason)
            } else {
                null
            }

            item.copy(
                // The hero renders the portrait: TVDB's 680x1000 poster and TMDB's w780 both beat
                // AniList's ~460px cover on a tall phone screen.
                poster = seasonPoster
                    ?: tvdbArtwork.posterUrl?.takeIf { mappedSeason == null || mappedSeason <= 1 }
                    ?: tmdbArtwork.posterUrl
                    ?: item.poster,
                banner = tmdbArtwork.backdropUrl
                    ?: tvdbArtwork.backgroundUrl
                    ?: fanartArtwork.backgroundUrl
                    ?: aniZip?.fanartUrl()
                    ?: item.banner,
                logo = fanartArtwork.logoUrl
                    ?: tvdbArtwork.clearLogoUrl
                    ?: aniZip?.clearLogoUrl()
                    ?: tmdbArtwork.logoUrl
                    ?: item.logo,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Cache the untouched item so a transient failure does not re-fire on every publish.
            item
        }
    }

    private suspend fun fanartFor(
        item: MetaPreview,
        aniZip: AniZipResponse?,
        tmdbId: String?,
    ): FanartArtwork {
        val isMovie = item.type.equals("movie", ignoreCase = true)
        // Anime films usually have no TVDB id at all, which is exactly where fanart's movie
        // endpoint earns its place — ani.zip has no Clearlogo for them either.
        if (isMovie) return tmdbId?.let { FanartClient.movieArtwork(it) } ?: FanartArtwork()
        return aniZip?.tvdbId()?.let { FanartClient.tvArtwork(it) } ?: FanartArtwork()
    }
}
