package com.nuvio.app.features.anivio

import com.nuvio.app.core.anivio.AnivioClient
import com.nuvio.app.core.anivio.AnivioMedia
import com.nuvio.app.core.anivio.backgroundUrl
import com.nuvio.app.core.anivio.logoUrl
import com.nuvio.app.core.anivio.posterUrl
import com.nuvio.app.core.anilist.stripAniListMarkup
import com.nuvio.app.features.anilist.parseAniListMediaId
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.formatRuntimeFromMinutes
import com.nuvio.app.features.tracking.TrackingSettingsRepository

/** Metadata-only fallback used when AniList is unavailable. */
object AnivioMetaSource {
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
        return media.toMetaDetails(id = id, type = type)
    }

    private fun AnivioMedia.toMetaDetails(id: String, type: String): MetaDetails {
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
            runtime = duration?.takeIf { it > 0 }?.let(::formatRuntimeFromMinutes),
            genres = genres,
        )
    }
}
