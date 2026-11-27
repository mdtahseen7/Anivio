package com.nuvio.app.features.anivio

import com.nuvio.app.core.anivio.AnivioClient
import com.nuvio.app.core.anivio.posterUrl
import com.nuvio.app.features.anilist.isAniListId
import com.nuvio.app.features.anilist.parseAniListMediaId
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import kotlinx.coroutines.CancellationException

/** Replaces card posters with Anivio artwork when it has a matching AniList record. */
suspend fun enrichPreviewPostersWithAnivio(items: List<MetaPreview>): List<MetaPreview> {
    TrackingSettingsRepository.ensureLoaded()
    if (!TrackingSettingsRepository.uiState.value.anivioAssetsEnabled) return items

    val ids = items.mapNotNull { if (isAniListId(it.id)) parseAniListMediaId(it.id) else null }
    if (ids.isEmpty()) return items
    val artwork = try {
        AnivioClient.previewArtwork(ids)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        return items
    }
    return items.map { item ->
        val media = (if (isAniListId(item.id)) parseAniListMediaId(item.id) else null)
            ?.let(artwork::get) ?: return@map item
        item.copy(
            poster = media.posterUrl ?: item.poster,
            countryOfOrigin = item.countryOfOrigin ?: media.countryOfOrigin,
            mediaFormat = item.mediaFormat ?: media.format,
            episodeDurationMinutes = item.episodeDurationMinutes ?: media.duration?.takeIf { it > 0 },
            artworkColor = item.artworkColor ?: media.coverImage?.color,
        )
    }
}
