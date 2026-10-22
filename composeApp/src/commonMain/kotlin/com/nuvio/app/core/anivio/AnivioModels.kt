package com.nuvio.app.core.anivio

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive

/** A Media record returned by the Anivio GraphQL API. */
@Serializable
data class AnivioMedia(
    val id: Int,
    val idMal: Int? = null,
    val title: AnivioTitle? = null,
    val type: String? = null,
    val format: String? = null,
    val status: String? = null,
    val description: String? = null,
    val startDate: AnivioFuzzyDate? = null,
    val endDate: AnivioFuzzyDate? = null,
    val source: String? = null,
    val hashtag: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val trending: Int? = null,
    val favourites: Int? = null,
    val countryOfOrigin: String? = null,
    val isAdult: Boolean = false,
    val genres: List<String> = emptyList(),
    val tags: List<AnivioTag> = emptyList(),
    val externalLinks: List<AnivioExternalLink> = emptyList(),
    val studios: AnivioStudioConnection? = null,
    val nextAiringEpisode: AnivioAiringSchedule? = null,
    val stats: AnivioStats? = null,
    val siteUrl: String? = null,
    val coverImage: AnivioCoverImage? = null,
    val bannerImage: String? = null,
    val images: List<AnivioImage> = emptyList(),
    val artworks: List<AnivioArtwork> = emptyList(),
)

@Serializable
data class AnivioTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class AnivioFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class AnivioCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val medium: String? = null,
    val color: String? = null,
)

@Serializable
data class AnivioImage(
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    val url: String,
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val type: String? = null,
    val source: String? = null,
)

@Serializable
data class AnivioArtwork(
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    val url: String,
    val large: String? = null,
    val medium: String? = null,
    val type: String? = null,
    val source: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val language: String? = null,
    val isAdult: Boolean = false,
)

@Serializable
data class AnivioTag(
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val category: String? = null,
    val rank: Int? = null,
    val isMediaSpoiler: Boolean = false,
    val isAdult: Boolean = false,
)

@Serializable
data class AnivioExternalLink(
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    val url: String? = null,
    val site: String? = null,
    val type: String? = null,
)

@Serializable
data class AnivioStudio(
    val id: Int? = null,
    val name: String? = null,
    val isMain: Boolean = false,
)

@Serializable
data class AnivioStudioConnection(
    val nodes: List<AnivioStudio> = emptyList(),
)

@Serializable
data class AnivioAiringSchedule(
    val airingAt: Long? = null,
    val timeUntilAiring: Long? = null,
    val episode: Int? = null,
    val mediaId: Int? = null,
)

@Serializable
data class AnivioStats(
    val scoreDistribution: List<AnivioScoreDistribution> = emptyList(),
    val statusDistribution: List<AnivioStatusDistribution> = emptyList(),
)

@Serializable
data class AnivioScoreDistribution(
    val score: Int? = null,
    val amount: Int? = null,
)

@Serializable
data class AnivioStatusDistribution(
    val status: String? = null,
    val amount: Int? = null,
)

/** Heavy, opt-in detail data. Keep this out of catalog and core metadata requests. */
@Serializable
data class AnivioMediaExtras(
    val characters: List<AnivioCharacterEdge> = emptyList(),
    val relations: List<AnivioRelationEdge> = emptyList(),
    val recommendations: List<AnivioMedia> = emptyList(),
    val videos: List<AnivioVideo> = emptyList(),
    val screenshots: List<AnivioScreenshot> = emptyList(),
)

@Serializable
data class AnivioCharacterEdge(
    val role: String? = null,
    val node: AnivioCharacter? = null,
    val voiceActors: List<AnivioStaff> = emptyList(),
)

@Serializable
data class AnivioCharacter(
    val id: Int,
    val name: AnivioCharacterName? = null,
    val image: AnivioCharacterImage? = null,
    val description: String? = null,
    val gender: String? = null,
    val dateOfBirth: AnivioFuzzyDate? = null,
    val age: String? = null,
    val bloodType: String? = null,
    val siteUrl: String? = null,
)

@Serializable
data class AnivioCharacterName(
    val first: String? = null,
    val middle: String? = null,
    val last: String? = null,
    val full: String? = null,
    val native: String? = null,
    val alternative: List<String> = emptyList(),
    val alternativeSpoiler: List<String> = emptyList(),
)

@Serializable
data class AnivioCharacterImage(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class AnivioStaff(
    val id: Int,
    val name: AnivioStaffName? = null,
    val image: AnivioCharacterImage? = null,
    val siteUrl: String? = null,
)

@Serializable
data class AnivioStaffName(
    val first: String? = null,
    val middle: String? = null,
    val last: String? = null,
    val full: String? = null,
    val native: String? = null,
    val alternative: List<String> = emptyList(),
)

@Serializable
data class AnivioRelationEdge(
    val relationType: String? = null,
    val node: AnivioMedia? = null,
)

@Serializable
data class AnivioVideo(
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    val url: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val artist: String? = null,
    val type: String? = null,
    val source: String? = null,
)

@Serializable
data class AnivioScreenshot(
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    val url: String? = null,
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val order: Int? = null,
    val source: String? = null,
)

@Serializable
data class AnivioEpisode(
    @Serializable(with = FlexibleIdSerializer::class) val id: String? = null,
    val number: Int,
    val title: String? = null,
    val overview: String? = null,
    val airDate: String? = null,
    val runtime: Int? = null,
    val image: AnivioEpisodeImage? = null,
)

@Serializable
data class AnivioEpisodeImage(
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
data class AnivioIdMapping(
    @SerialName("anilist_id") val anilistId: Int? = null,
    @SerialName("mal_id") val malId: Int? = null,
    @SerialName("kitsu_id") val kitsuId: Int? = null,
    @SerialName("anidb_id") val anidbId: Int? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
)

@Serializable
data class AnivioMappedEpisode(
    val image: String? = null,
    val title: Map<String, String> = emptyMap(),
)

@Serializable
data class AnivioMapping(
    val mappings: AnivioIdMapping? = null,
    val episodes: Map<String, AnivioMappedEpisode> = emptyMap(),
)

enum class AnivioMediaSort(val wireName: String) {
    ID_DESC("ID_DESC"),
    TITLE_ROMAJI("TITLE_ROMAJI"),
    TITLE_ROMAJI_DESC("TITLE_ROMAJI_DESC"),
    SCORE_DESC("SCORE_DESC"),
    POPULARITY_DESC("POPULARITY_DESC"),
    TRENDING_DESC("TRENDING_DESC"),
    FAVOURITES_DESC("FAVOURITES_DESC"),
    EPISODES_DESC("EPISODES_DESC"),
    DURATION_DESC("DURATION_DESC"),
    START_DATE_DESC("START_DATE_DESC"),
    UPDATED_AT_DESC("UPDATED_AT_DESC"),
}

data class AnivioCatalogQuery(
    val page: Int = 1,
    val sort: AnivioMediaSort = AnivioMediaSort.TRENDING_DESC,
    val statusIn: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val genre: String? = null,
    val averageScoreGreater: Int? = null,
)

/** Convenient poster selection: Anivio images first, then the AniList-compatible cover image. */
val AnivioMedia.posterUrl: String?
    get() = images.firstOrNull { it.type.equals("poster", ignoreCase = true) }?.bestUrl
        ?: coverImage?.extraLarge
        ?: coverImage?.large
        ?: coverImage?.medium

/** Convenient hero selection: prefer a background image and fall back to the AniList banner. */
val AnivioMedia.backgroundUrl: String?
    get() = images.firstOrNull { it.type.equals("background", ignoreCase = true) }?.bestUrl
        ?: artworks.firstOrNull { it.type.equals("background", ignoreCase = true) }?.bestUrl
        ?: bannerImage

/** Prefer the best transparent title treatment for hero/detail branding. */
val AnivioMedia.logoUrl: String?
    get() = listOf("hdtvlogo", "clearlogo", "logo").asSequence()
        .flatMap { preferredType ->
            artworks.asSequence().filter { it.type.equals(preferredType, ignoreCase = true) }
        }
        .firstOrNull()?.bestUrl
        ?: images.firstOrNull { it.type.equals("logo", ignoreCase = true) }?.bestUrl

val AnivioImage.bestUrl: String
    get() = large ?: medium ?: small ?: url

val AnivioArtwork.bestUrl: String
    get() = large ?: medium ?: url

/** GraphQL ID values are commonly encoded as either JSON strings or JSON numbers. */
@OptIn(ExperimentalSerializationApi::class)
internal object FlexibleIdSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AnivioId", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val primitive = jsonDecoder.decodeJsonElement() as? JsonPrimitive
            ?: throw SerializationException("Anivio ID was not a JSON primitive")
        return primitive.content
    }

    override fun serialize(encoder: Encoder, value: String?) {
        val jsonEncoder = encoder as? JsonEncoder
        if (value == null) encoder.encodeNull()
        else if (jsonEncoder != null) jsonEncoder.encodeJsonElement(JsonPrimitive(value))
        else encoder.encodeString(value)
    }
}
