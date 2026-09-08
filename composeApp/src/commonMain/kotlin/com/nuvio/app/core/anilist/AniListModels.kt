package com.nuvio.app.core.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wrapper for a paged `Page { media { ... } }` selection. */
@Serializable
data class AniListPage(
    val pageInfo: AniListPageInfo? = null,
    val media: List<AniListMedia> = emptyList(),
)

/** Wrapper for a paged `Page { airingSchedules { ... } }` selection. */
@Serializable
data class AniListAiringPage(
    val pageInfo: AniListPageInfo? = null,
    val airingSchedules: List<AniListAiringSchedule> = emptyList(),
)

@Serializable
data class AniListAiringSchedule(
    val episode: Int? = null,
    val airingAt: Long? = null,
    val media: AniListMedia? = null,
)

@Serializable
data class AniListPageInfo(
    val currentPage: Int? = null,
    val hasNextPage: Boolean = false,
)

@Serializable
data class AniListMedia(
    val id: Int,
    val idMal: Int? = null,
    /** ANIME or MANGA. Only requested by the list queries. */
    val type: String? = null,
    /** TV, TV_SHORT, MOVIE, SPECIAL, OVA, ONA, MUSIC — or MANGA/NOVEL/ONE_SHOT for manga. */
    val format: String? = null,
    /** FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED, HIATUS. */
    val status: String? = null,
    val episodes: Int? = null,
    /** Manga only. */
    val chapters: Int? = null,
    /** Manga only. */
    val volumes: Int? = null,
    val duration: Int? = null,
    /** 0-100; divided by 10 for the app's 0-10 rating display. */
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val isAdult: Boolean = false,
    val genres: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val title: AniListTitle? = null,
    val coverImage: AniListCoverImage? = null,
    val bannerImage: String? = null,
    val startDate: AniListFuzzyDate? = null,
    val description: String? = null,
)

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    @SerialName("native") val nativeTitle: String? = null,
)

@Serializable
data class AniListCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null,
)

/**
 * AniList dates are "fuzzy" — any component may be missing for announced-but-undated titles.
 */
@Serializable
data class AniListFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
) {
    /** Full ISO date, or null when the date is too fuzzy to be one. */
    fun toIsoDateOrNull(): String? {
        val year = year ?: return null
        val month = month ?: return null
        val day = day ?: return null
        return "$year-${month.pad2()}-${day.pad2()}"
    }
}

private fun Int.pad2(): String = toString().padStart(2, '0')

/**
 * GraphQL selection set matching [AniListMedia]. Kept next to the model so the two stay in step —
 * add a field here whenever one is added there.
 */
const val ANILIST_MEDIA_FIELDS: String = """
    id
    idMal
    format
    status
    episodes
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
    description(asHtml: false)
"""

