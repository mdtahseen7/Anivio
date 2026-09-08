package com.nuvio.app.features.tmdb

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A wide backdrop, a tall poster, and a transparent title logo, as far as TMDB could supply them. */
data class TmdbArtwork(
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val logoUrl: String? = null,
)

/**
 * Focused wrapper over TMDB's `/images` endpoint, for callers that want artwork without the full
 * [TmdbMetadataService] enrichment pass — the home hero being the case in point.
 *
 * Backdrops are picked textless-first (`iso_639_1` absent), because the hero draws the title over
 * them; logos are picked English-first.
 */
object TmdbArtworkSource {
    private const val BACKDROP_SIZE = "w1280"
    private const val POSTER_SIZE = "w780"
    private const val LOGO_SIZE = "w500"

    private val log = Logger.withTag("TmdbArtworkSource")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun artwork(
        tmdbId: String,
        mediaType: String,
        language: String = "en",
    ): TmdbArtwork {
        val settings = TmdbSettingsRepository.snapshot()
        if (!settings.enabled || !settings.hasApiKey) return TmdbArtwork()

        val endpointType = TmdbService.normalizeMediaType(mediaType).takeIf { it == "movie" } ?: "tv"
        val url = buildTmdbUrl(
            endpoint = "$endpointType/$tmdbId/images",
            apiKey = settings.apiKey.trim(),
            // "null" keeps textless artwork in the response — TMDB drops it otherwise.
            query = mapOf("include_image_language" to "$language,en,null"),
        )

        return runCatching {
            val response = json.decodeFromString<TmdbArtworkImagesResponse>(httpGetText(url))
            TmdbArtwork(
                backdropUrl = response.backdrops.bestBackdrop()?.let { imageUrl(it.filePath, BACKDROP_SIZE) },
                // Textless first here too — the hero draws its own logo and metadata over the art.
                posterUrl = response.posters.bestBackdrop()?.let { imageUrl(it.filePath, POSTER_SIZE) },
                logoUrl = response.logos.bestLogo(language)?.let { imageUrl(it.filePath, LOGO_SIZE) },
            )
        }.onFailure { error ->
            log.w(error) { "TMDB images lookup failed for $endpointType/$tmdbId" }
        }.getOrDefault(TmdbArtwork())
    }

    /**
     * Poster for one season of a show.
     *
     * TMDB's show-level poster is the first season's, so a later-season entry needs this instead —
     * `/tv/{id}/season/{n}` carries that season's own key art at full resolution.
     */
    suspend fun seasonPosterUrl(tmdbId: String, seasonNumber: Int, language: String = "en"): String? {
        val settings = TmdbSettingsRepository.snapshot()
        if (!settings.enabled || !settings.hasApiKey || seasonNumber < 0) return null

        val url = buildTmdbUrl(
            endpoint = "tv/$tmdbId/season/$seasonNumber",
            apiKey = settings.apiKey.trim(),
            query = mapOf("language" to language),
        )
        return runCatching {
            json.decodeFromString<TmdbSeasonResponse>(httpGetText(url))
                .posterPath
                ?.let { imageUrl(it, POSTER_SIZE) }
        }.onFailure { error ->
            log.w(error) { "TMDB season lookup failed for tv/$tmdbId season $seasonNumber" }
        }.getOrNull()
    }

    /** Textless first, then widest, then best rated — the hero overlays its own title text. */
    private fun List<TmdbArtworkImage>.bestBackdrop(): TmdbArtworkImage? {
        if (isEmpty()) return null
        val textless = filter { it.language.isNullOrBlank() }
        return (textless.ifEmpty { this })
            .sortedWith(
                compareByDescending<TmdbArtworkImage> { it.voteAverage ?: 0.0 }
                    .thenByDescending { it.width ?: 0 },
            )
            .firstOrNull()
    }

    private fun List<TmdbArtworkImage>.bestLogo(language: String): TmdbArtworkImage? {
        if (isEmpty()) return null
        val ranked = sortedWith(
            compareByDescending<TmdbArtworkImage> { it.voteAverage ?: 0.0 }
                .thenByDescending { it.width ?: 0 },
        )
        return ranked.firstOrNull { it.language == language }
            ?: ranked.firstOrNull { it.language == "en" }
            ?: ranked.firstOrNull()
    }

    private fun imageUrl(path: String?, size: String): String? {
        val clean = path?.takeIf { it.isNotBlank() } ?: return null
        return "https://image.tmdb.org/t/p/$size$clean"
    }
}

@Serializable
private data class TmdbSeasonResponse(
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
private data class TmdbArtworkImagesResponse(
    val backdrops: List<TmdbArtworkImage> = emptyList(),
    val posters: List<TmdbArtworkImage> = emptyList(),
    val logos: List<TmdbArtworkImage> = emptyList(),
)

@Serializable
private data class TmdbArtworkImage(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val width: Int? = null,
)
