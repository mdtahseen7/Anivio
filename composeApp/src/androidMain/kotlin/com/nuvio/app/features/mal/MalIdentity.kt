package com.nuvio.app.features.mal

import com.nuvio.app.core.anilist.AniZipClient
import com.nuvio.app.core.anilist.malId
import com.nuvio.app.features.tracking.TrackingMediaReference

internal const val MAL_ID_PREFIX = "mal:"
internal const val MAL_SEASON = 1

internal fun malAnimeIdOf(contentId: String?): Int? {
    val value = contentId?.trim() ?: return null
    if (!value.startsWith(MAL_ID_PREFIX, ignoreCase = true)) return null
    return value.substring(MAL_ID_PREFIX.length).substringBefore(':').toIntOrNull()?.takeIf { it > 0 }
}

/** Explicit MAL ids win; the only fallback is an explicit AniList id reversed through ani.zip. */
internal suspend fun TrackingMediaReference.resolveMalAnimeId(): Int? {
    ids.mal?.toPositiveInt()?.let { return it }
    catalog?.contentId?.let(::malAnimeIdOf)?.let { return it }
    val aniListId = ids.anilist?.toPositiveInt()
        ?: catalog?.contentId?.let(::aniListAnimeIdOf)
        ?: return null
    return AniZipClient.mappings(aniListId)?.malId()?.toIntOrNull()?.takeIf { it > 0 }
}

private fun aniListAnimeIdOf(contentId: String): Int? {
    val value = contentId.trim()
    if (!value.startsWith("anilist:", ignoreCase = true)) return null
    return value.substring("anilist:".length).substringBefore(':').toIntOrNull()?.takeIf { it > 0 }
}

private fun Long.toPositiveInt(): Int? = takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
