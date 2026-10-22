package com.nuvio.app.features.anivio

import com.nuvio.app.core.anivio.AnivioClient
import com.nuvio.app.core.anivio.AnivioEpisode
import com.nuvio.app.core.anivio.AnivioMedia
import com.nuvio.app.core.anivio.backgroundUrl
import com.nuvio.app.core.anivio.logoUrl
import com.nuvio.app.core.anivio.posterUrl
import com.nuvio.app.core.anilist.stripAniListMarkup
import com.nuvio.app.features.anilist.parseAniListMediaId
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaCompany
import com.nuvio.app.features.details.MetaLink
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.formatRuntimeFromMinutes
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Metadata-only fallback used when AniList is unavailable. */
object AnivioMetaSource {
    private const val EPISODE_LIST_TIMEOUT_MS = 3_000L

    suspend fun fetchMeta(
        id: String,
        type: String,
        forceRefresh: Boolean = false,
    ): MetaDetails? {
        TrackingSettingsRepository.ensureLoaded()
        val settings = TrackingSettingsRepository.uiState.value
        if (!settings.anivioMetadataFallbackEnabled) return null
        val mediaId = parseAniListMediaId(id) ?: return null
        val media = AnivioClient.mediaByAniListIdOrNull(mediaId, forceRefresh) ?: return null
        // Core detail is sufficient to paint the page. The episode list is a separate lookup so a
        // slow/partial extension field never prevents the existing stream add-ons from seeing the
        // canonical anilist:s:e video ids. Anivio has no server-side episode pagination, so the
        // complete list is retained and the details UI must render it lazily.
        val episodes = if (type.equals("movie", ignoreCase = true)) {
            emptyList()
        } else {
            withTimeoutOrNull(EPISODE_LIST_TIMEOUT_MS) {
                try {
                    AnivioClient.episodesByAniListId(mediaId, forceRefresh)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                }
            }.orEmpty()
        }
        return media.toMetaDetails(id = id, type = type, episodes = episodes)
    }

    private fun AnivioMedia.toMetaDetails(
        id: String,
        type: String,
        episodes: List<AnivioEpisode>,
    ): MetaDetails {
        return MetaDetails(
            id = id,
            type = type,
            name = title?.english ?: title?.romaji ?: title?.native ?: id,
            poster = posterUrl,
            background = backgroundUrl,
            logo = logoUrl,
            description = description?.stripAniListMarkup(),
            releaseInfo = seasonYear?.toString(),
            status = status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
            imdbRating = averageScore?.takeIf { it > 0 }?.let { (it / 10.0).toString() },
            ageRating = "18+".takeIf { isAdult },
            runtime = duration?.takeIf { it > 0 }?.let(::formatRuntimeFromMinutes),
            genres = genres,
            productionCompanies = studios?.nodes.orEmpty()
                .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .distinct()
                .map(::MetaCompany),
            country = countryOfOrigin,
            website = externalLinks.firstOrNull { it.type.equals("INFO", ignoreCase = true) }?.url,
            links = externalLinks.mapNotNull { link ->
                val url = link.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                MetaLink(
                    name = link.site?.takeIf(String::isNotBlank) ?: "External link",
                    category = link.type?.takeIf(String::isNotBlank) ?: "INFO",
                    url = url,
                )
            },
            videos = buildAnivioEpisodeVideos(id = id, type = type, episodeCount = this.episodes, episodes = episodes),
        )
    }
}

/** Converts Anivio's episode metadata into the app's canonical, stream-provider-compatible ids. */
internal fun buildAnivioEpisodeVideos(
    id: String,
    type: String,
    episodeCount: Int?,
    episodes: List<AnivioEpisode>,
): List<MetaVideo> {
    if (type.equals("movie", ignoreCase = true)) return emptyList()

    val numbers = episodes.map { it.number }.distinct().sorted()
    val renderedNumbers = if (numbers.isNotEmpty()) {
        numbers
    } else {
        (1..(episodeCount ?: 0).coerceAtMost(1000)).toList()
    }
    return renderedNumbers.map { number ->
        val episode = episodes.firstOrNull { it.number == number }
        MetaVideo(
            // Keep the same canonical identity used by AniList and stream add-ons. Anivio
            // contributes metadata only; it never contributes stream URLs.
            id = "$id:1:$number",
            title = episode?.title?.takeIf(String::isNotBlank) ?: "Episode $number",
            released = episode?.airDate,
            thumbnail = episode?.image?.large ?: episode?.image?.medium ?: episode?.image?.small,
            season = 1,
            episode = number,
            overview = episode?.overview,
            runtime = episode?.runtime,
        )
    }
}
