package com.nuvio.app.core.anilist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The fuller AniList `Media` shape used by the details page. Kept separate from [AniListMedia] so
 * the home-row query stays small — those rows request none of these extra connections.
 */
@Serializable
data class AniListMediaDetail(
    val id: Int,
    val idMal: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val isAdult: Boolean = false,
    val genres: List<String> = emptyList(),
    val countryOfOrigin: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val title: AniListTitle? = null,
    val coverImage: AniListCoverImage? = null,
    val bannerImage: String? = null,
    val startDate: AniListFuzzyDate? = null,
    val endDate: AniListFuzzyDate? = null,
    val description: String? = null,
    val studios: AniListStudioConnection? = null,
    val trailer: AniListTrailer? = null,
    val nextAiringEpisode: AniListNextAiringEpisode? = null,
    val characters: AniListCharacterConnection? = null,
    val staff: AniListStaffConnection? = null,
    val externalLinks: List<AniListExternalLink> = emptyList(),
    val recommendations: AniListRecommendationConnection? = null,
    val airingSchedule: AniListAiringScheduleConnection? = null,
)

@Serializable
data class AniListAiringScheduleConnection(val nodes: List<AniListAiringScheduleNode> = emptyList())

/**
 * One scheduled broadcast. This is the same data LiveChart and every other anime calendar renders —
 * AniList publishes it directly, so there is nothing to scrape.
 */
@Serializable
data class AniListAiringScheduleNode(
    val episode: Int? = null,
    /** Unix seconds, not millis. */
    val airingAt: Long? = null,
)

/** Upcoming broadcasts by episode number, in epoch millis. */
fun AniListMediaDetail.airingScheduleByEpisode(): Map<Int, Long> =
    airingSchedule?.nodes.orEmpty()
        .mapNotNull { node ->
            val episode = node.episode ?: return@mapNotNull null
            val airingAt = node.airingAt?.takeIf { it > 0 } ?: return@mapNotNull null
            episode to airingAt * 1000L
        }
        .toMap()

@Serializable
data class AniListRecommendationConnection(val nodes: List<AniListRecommendationNode> = emptyList())

@Serializable
data class AniListRecommendationNode(
    /** Community upvotes minus downvotes. Negative means users disagreed with the suggestion. */
    val rating: Int? = null,
    val mediaRecommendation: AniListMedia? = null,
)

@Serializable
data class AniListStudioConnection(val nodes: List<AniListStudio> = emptyList())

@Serializable
data class AniListStudio(
    val id: Int? = null,
    val name: String,
    val isAnimationStudio: Boolean = false,
)

@Serializable
data class AniListTrailer(
    val id: String? = null,
    val site: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class AniListNextAiringEpisode(
    val airingAt: Long? = null,
    val episode: Int? = null,
)

@Serializable
data class AniListCharacterConnection(val edges: List<AniListCharacterEdge> = emptyList())

@Serializable
data class AniListCharacterEdge(
    /** MAIN, SUPPORTING or BACKGROUND. */
    val role: String? = null,
    val node: AniListNamedEntity? = null,
    val voiceActors: List<AniListNamedEntity> = emptyList(),
)

@Serializable
data class AniListStaffConnection(val edges: List<AniListStaffEdge> = emptyList())

@Serializable
data class AniListStaffEdge(
    /** Free text, e.g. "Director", "Original Creator", "Series Composition". */
    val role: String? = null,
    val node: AniListNamedEntity? = null,
)

@Serializable
data class AniListNamedEntity(
    val name: AniListPersonName? = null,
    val image: AniListImage? = null,
)

@Serializable
data class AniListPersonName(
    val full: String? = null,
    @SerialName("native") val nativeName: String? = null,
)

@Serializable
data class AniListImage(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class AniListExternalLink(
    val site: String? = null,
    val url: String? = null,
    val type: String? = null,
)

/** GraphQL selection matching [AniListMediaDetail]. Keep in step with the model above. */
const val ANILIST_MEDIA_DETAIL_FIELDS: String = """
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
    countryOfOrigin
    season
    seasonYear
    title { romaji english native }
    coverImage { extraLarge large color }
    bannerImage
    startDate { year month day }
    endDate { year month day }
    description(asHtml: false)
    studios { nodes { id name isAnimationStudio } }
    trailer { id site thumbnail }
    nextAiringEpisode { airingAt episode }
    characters(sort: [ROLE, RELEVANCE], perPage: 16) {
        edges {
            role
            node { name { full native } image { large medium } }
            voiceActors(language: JAPANESE, sort: RELEVANCE) { name { full native } image { large medium } }
        }
    }
    staff(sort: RELEVANCE, perPage: 20) {
        edges {
            role
            node { name { full native } image { large medium } }
        }
    }
    externalLinks { site url type }
    recommendations(sort: RATING_DESC, perPage: 20) {
        nodes {
            rating
            mediaRecommendation { $ANILIST_MEDIA_FIELDS }
        }
    }
    airingSchedule(notYetAired: true, perPage: 50) {
        nodes { episode airingAt }
    }
"""
