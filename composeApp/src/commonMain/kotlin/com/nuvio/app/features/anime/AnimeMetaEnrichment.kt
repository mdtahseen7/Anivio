package com.nuvio.app.features.anime

import com.nuvio.app.core.anilist.AniZipResponse
import com.nuvio.app.core.anilist.KitsuClient
import com.nuvio.app.core.anilist.KitsuEpisode
import com.nuvio.app.core.anilist.TvdbArtwork
import com.nuvio.app.core.anilist.TvdbClient
import com.nuvio.app.core.anilist.TvdbEpisode
import com.nuvio.app.core.anilist.kitsuId
import com.nuvio.app.core.anilist.mappedSeasonNumber
import com.nuvio.app.core.anilist.tvdbId
import com.nuvio.app.features.anilist.ANILIST_SEASON
import com.nuvio.app.features.details.MetaVideo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Per-title metadata gathered from the sources that sit behind whichever provider is active.
 *
 * AniList and MAL both supply only a thin record — titles, a cover, an episode count. Everything
 * that makes a details screen look finished (per-episode stills, episode titles, overviews, air
 * dates, a 1920x1080 background, a clear logo) comes from ani.zip, Kitsu and TVDB, none of which
 * care which provider asked. Keeping the gathering here means a MAL-sourced title is as rich as an
 * AniList-sourced one instead of falling back to "Episode 3" with no artwork.
 */
data class AnimeEnrichment(
    val aniZip: AniZipResponse? = null,
    val kitsuEpisodes: Map<Int, KitsuEpisode> = emptyMap(),
    val tvdbEpisodes: Map<Int, TvdbEpisode> = emptyMap(),
    val tvdbArtwork: TvdbArtwork = TvdbArtwork(),
    val mappedSeason: Int? = null,
    /**
     * Exact upcoming broadcast times by episode number, in epoch millis. Only AniList publishes
     * these; the MAL path leaves it empty and episode notifications fall back to the air date.
     */
    val airingScheduleByEpisode: Map<Int, Long> = emptyMap(),
)

/**
 * Fetches Kitsu episodes, TVDB episodes and TVDB series artwork concurrently for [aniZip].
 *
 * Every lookup is best-effort: they hang off ids that ani.zip may not carry, and a title with no
 * TVDB mapping should still render from whatever the provider gave us.
 */
suspend fun loadAnimeEnrichment(
    aniZip: AniZipResponse?,
    isMovie: Boolean,
    expectedEpisodeCount: Int?,
): AnimeEnrichment {
    if (aniZip == null) return AnimeEnrichment()
    val mappedSeason = aniZip.mappedSeasonNumber()
    val seriesId = aniZip.tvdbId()

    // ani.zip serves the same TVDB record these two lookups exist to supplement, so on a finished
    // show it usually already carries every title, still and overview. Asking anyway cost a TVDB
    // request plus up to five sequential Kitsu pages for data we had — the single largest source of
    // avoidable traffic on a details open. Each is now requested only for what is actually missing.
    val gaps = aniZip.episodeGaps(expectedEpisodeCount, isMovie)

    // The one case where ani.zip having an image is not good enough. ani.zip and TVDB model a
    // multi-season anime as one show, so their stills can be from any cour of the franchise; Kitsu's
    // ids are per-cour, so its stills are guaranteed to be this entry's. For a first season or a
    // single-cour show the two are the same show and the request is pure waste.
    val mapsToLaterSeason = !isMovie && (mappedSeason ?: 1) > 1

    return coroutineScope {
        // No error trapping here on purpose: KitsuClient and TvdbClient already log and degrade to
        // empty on failure, and wrapping them would also swallow CancellationException, which would
        // detach these requests from the caller's scope when a screen is closed mid-load.
        val kitsuDeferred = async {
            // Kitsu's value here is episode stills; its titles are romanised and rank below both
            // other sources. No missing thumbnail and no season ambiguity means nothing to gain.
            if (!gaps.needsThumbnails && !mapsToLaterSeason) {
                emptyMap()
            } else {
                aniZip.kitsuId()?.let { kitsuId ->
                    KitsuClient.episodes(kitsuId = kitsuId, expectedCount = expectedEpisodeCount)
                }.orEmpty()
            }
        }
        val tvdbEpisodesDeferred = async {
            // Without a mapped season every request would return season 1, which is worse than
            // nothing for a sequel: wrong stills and wrong titles look like corruption.
            if (seriesId == null || mappedSeason == null) {
                emptyMap()
            } else if (!gaps.needsTitles && !gaps.needsThumbnails) {
                emptyMap()
            } else {
                TvdbClient.episodes(seriesId = seriesId, seasonNumber = mappedSeason)
            }
        }
        // Always worth asking: ani.zip offers one pre-picked image per type, this picks by score and
        // language, and it is disk-cached for a month so it is free on any revisit.
        val artworkDeferred = async {
            seriesId?.let { TvdbClient.seriesArtwork(it) } ?: TvdbArtwork()
        }

        AnimeEnrichment(
            aniZip = aniZip,
            kitsuEpisodes = kitsuDeferred.await(),
            tvdbEpisodes = tvdbEpisodesDeferred.await(),
            tvdbArtwork = artworkDeferred.await(),
            mappedSeason = mappedSeason,
        )
    }
}

/** What ani.zip is missing for the episodes that will actually be rendered. */
private data class AniZipEpisodeGaps(
    val needsTitles: Boolean,
    val needsThumbnails: Boolean,
)

/**
 * Which enrichment lookups would add anything.
 *
 * A currently-airing show is the case that needs them: ani.zip emits `title.en: null` and no image
 * for episodes that have aired but not yet been catalogued. A finished show is usually complete, so
 * both lookups can be skipped outright.
 */
private fun AniZipResponse.episodeGaps(
    expectedEpisodeCount: Int?,
    isMovie: Boolean,
): AniZipEpisodeGaps {
    if (isMovie) return AniZipEpisodeGaps(needsTitles = false, needsThumbnails = false)

    val total = expectedEpisodeCount?.takeIf { it > 0 }
        ?: episodeCount?.takeIf { it > 0 }
        ?: episodes.keys.mapNotNull(String::toIntOrNull).maxOrNull()
        // Nothing to size the walk with. Ask both sources rather than render bare episodes.
        ?: return AniZipEpisodeGaps(needsTitles = true, needsThumbnails = true)

    var missingTitles = false
    var missingThumbnails = false
    for (number in 1..total) {
        val episode = episodes[number.toString()]
        if (episode?.displayTitle.isNullOrBlank()) missingTitles = true
        if (episode?.image.isNullOrBlank()) missingThumbnails = true
        if (missingTitles && missingThumbnails) break
    }
    return AniZipEpisodeGaps(needsTitles = missingTitles, needsThumbnails = missingThumbnails)
}

/**
 * One [MetaVideo] per episode, merging the provider's count with the enrichment sources.
 *
 * Walks `1..total` rather than ani.zip's key set because that map also holds specials under
 * `S`-prefixed keys. [episodeCount] is allowed to be null or zero — MAL reports `num_episodes = 0`
 * for anything still airing, so falling through to ani.zip's count is what keeps a currently-airing
 * show from rendering with no episodes at all.
 */
fun buildEnrichedEpisodes(
    videoIdPrefix: String,
    episodeCount: Int?,
    enrichment: AnimeEnrichment,
    seriesPoster: String?,
    fallbackTitle: (Int) -> String,
): List<MetaVideo> {
    val aniZip = enrichment.aniZip
    val total = episodeCount?.takeIf { it > 0 }
        ?: aniZip?.episodeCount?.takeIf { it > 0 }
        ?: aniZip?.episodes?.keys?.mapNotNull(String::toIntOrNull)?.maxOrNull()
        ?: return emptyList()

    return (1..total).map { number ->
        val episode = aniZip?.episodes?.get(number.toString())
        val kitsu = enrichment.kitsuEpisodes[number]
        val tvdb = enrichment.tvdbEpisodes[number]
        MetaVideo(
            id = "$videoIdPrefix:$ANILIST_SEASON:$number",
            // TVDB's English list first: ani.zip serves the same TVDB record but leaves `title.en`
            // null for many currently-airing episodes.
            title = tvdb?.title
                ?: episode?.displayTitle
                ?: kitsu?.title
                ?: fallbackTitle(number),
            released = episode?.releasedDate ?: tvdb?.airDate ?: kitsu?.airDate,
            // Kitsu first: its ids are per-cour, so its stills match this entry rather than the
            // whole franchise. TVDB then ani.zip cover what Kitsu has no art for.
            thumbnail = kitsu?.thumbnailUrl
                ?: tvdb?.imageUrl
                ?: episode?.image?.takeIf { it.isNotBlank() },
            seasonPoster = seriesPoster,
            season = ANILIST_SEASON,
            episode = number,
            overview = episode?.overviewText ?: tvdb?.overview ?: kitsu?.synopsis,
            runtime = episode?.runtimeMinutes ?: tvdb?.runtimeMinutes ?: kitsu?.runtimeMinutes,
            rating = episode?.ratingValue,
            airingAtEpochMs = enrichment.airingScheduleByEpisode[number],
        )
    }
}
