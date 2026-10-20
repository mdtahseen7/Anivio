package com.nuvio.app.features.details

import com.nuvio.app.core.anivio.AnivioClient
import com.nuvio.app.core.anivio.backgroundUrl
import com.nuvio.app.core.anivio.logoUrl
import com.nuvio.app.core.anivio.posterUrl
import com.nuvio.app.features.anilist.parseAniListMediaId
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import kotlinx.coroutines.withTimeoutOrNull

private const val ANIVIO_ENRICH_TIMEOUT_MS = 3_000L

/**
 * Adds Anivio's indexed artwork without making the details screen depend on Anivio being available.
 * Existing provider values always win; a failed, timed-out, missing, or partial Anivio response is
 * simply treated as no enrichment.
 */
suspend fun MetaDetails.enrichWithAnivioArtwork(): MetaDetails {
    if (type.equals("manga", ignoreCase = true)) return this
    TrackingSettingsRepository.ensureLoaded()
    if (!TrackingSettingsRepository.uiState.value.anivioAssetsEnabled) return this

    val anilistId = parseAniListMediaId(id) ?: return this
    val media = withTimeoutOrNull(ANIVIO_ENRICH_TIMEOUT_MS) {
        AnivioClient.mediaByAniListIdOrNull(anilistId)
    } ?: return this

    return copy(
        poster = poster ?: media.posterUrl,
        background = background ?: media.backgroundUrl,
        logo = logo ?: media.logoUrl,
    )
}
