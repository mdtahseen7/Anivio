package com.nuvio.app.core.anilist

import kotlinx.serialization.Serializable

/** A user's list for one status, as returned inside `MediaListCollection`. */
@Serializable
data class AniListMediaListGroup(
    val name: String? = null,
    /** CURRENT, PLANNING, COMPLETED, DROPPED, PAUSED or REPEATING. */
    val status: String? = null,
    val entries: List<AniListMediaListEntry> = emptyList(),
)

@Serializable
data class AniListMediaListCollection(
    val lists: List<AniListMediaListGroup> = emptyList(),
)

@Serializable
data class AniListMediaListEntry(
    val id: Int? = null,
    val status: String? = null,
    /** Episodes watched, or chapters read for manga. */
    val progress: Int? = null,
    val progressVolumes: Int? = null,
    val score: Double? = null,
    /** Epoch seconds. */
    val updatedAt: Long? = null,
    val media: AniListMedia? = null,
)

/**
 * Leaner selection than [ANILIST_MEDIA_FIELDS] for list reads: a user can have hundreds of entries,
 * and `description` alone would dominate the response. Adds the manga-only chapter/volume counts.
 */
const val ANILIST_LIST_MEDIA_FIELDS: String = """
    id
    idMal
    type
    format
    status
    episodes
    chapters
    volumes
    duration
    averageScore
    popularity
    isAdult
    genres
    season
    seasonYear
    title { romaji english native }
    coverImage { extraLarge large color }
    bannerImage
    startDate { year month day }
"""
