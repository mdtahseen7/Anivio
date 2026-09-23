package com.nuvio.app.core.anilist

import kotlinx.serialization.Serializable

/**
 * The signed-in user's aggregate anime statistics, straight from AniList's `User.statistics.anime`.
 * Every list here is what AniList itself computes server-side, so the stats screen renders the same
 * numbers anilist.co shows without re-deriving them from the raw list.
 */
@Serializable
data class AniListUserStatistics(
    val anime: AniListAnimeStatistics? = null,
)

@Serializable
data class AniListAnimeStatistics(
    val count: Int = 0,
    val meanScore: Double = 0.0,
    val standardDeviation: Double = 0.0,
    val minutesWatched: Long = 0,
    val episodesWatched: Int = 0,
    val statuses: List<AniListStatusStat> = emptyList(),
    val formats: List<AniListFormatStat> = emptyList(),
    val releaseYears: List<AniListReleaseYearStat> = emptyList(),
    val genres: List<AniListGenreStat> = emptyList(),
    val tags: List<AniListTagStat> = emptyList(),
    val studios: List<AniListStudioStat> = emptyList(),
    val scores: List<AniListScoreStat> = emptyList(),
)

@Serializable
data class AniListStatusStat(
    val status: String? = null,
    val count: Int = 0,
    val minutesWatched: Long = 0,
)

@Serializable
data class AniListFormatStat(
    val format: String? = null,
    val count: Int = 0,
    val minutesWatched: Long = 0,
)

@Serializable
data class AniListReleaseYearStat(
    val releaseYear: Int? = null,
    val count: Int = 0,
    val meanScore: Double = 0.0,
)

@Serializable
data class AniListGenreStat(
    val genre: String? = null,
    val count: Int = 0,
    val minutesWatched: Long = 0,
    val meanScore: Double = 0.0,
)

@Serializable
data class AniListTagStat(
    val tag: AniListTagName? = null,
    val count: Int = 0,
    val meanScore: Double = 0.0,
)

@Serializable
data class AniListTagName(
    val name: String? = null,
)

@Serializable
data class AniListStudioStat(
    val studio: AniListStudioName? = null,
    val count: Int = 0,
    val meanScore: Double = 0.0,
)

@Serializable
data class AniListStudioName(
    val name: String? = null,
)

@Serializable
data class AniListScoreStat(
    val score: Int = 0,
    val count: Int = 0,
)

/** The `statistics { anime { ... } }` selection. Kept lean — only the fields the stats screen draws. */
const val ANILIST_USER_STATISTICS_FIELDS: String = """
    anime {
        count
        meanScore
        standardDeviation
        minutesWatched
        episodesWatched
        statuses(sort: COUNT_DESC) { status count minutesWatched }
        formats(sort: COUNT_DESC) { format count minutesWatched }
        releaseYears(sort: COUNT_DESC) { releaseYear count meanScore }
        genres(sort: COUNT_DESC) { genre count minutesWatched meanScore }
        tags(sort: COUNT_DESC, limit: 10) { tag { name } count meanScore }
        studios(sort: COUNT_DESC, limit: 10) { studio { name } count meanScore }
        scores(sort: MEAN_SCORE) { score count }
    }
"""
