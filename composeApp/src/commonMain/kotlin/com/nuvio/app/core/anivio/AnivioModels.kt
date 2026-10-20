package com.nuvio.app.core.anivio

import kotlinx.serialization.KSerializer
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
    val episodes: Int? = null,
    val duration: Int? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val countryOfOrigin: String? = null,
    val isAdult: Boolean = false,
    val genres: List<String> = emptyList(),
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

/** Prefer transparent title treatments for hero/detail branding. */
val AnivioMedia.logoUrl: String?
    get() = artworks.firstOrNull {
        it.type.equals("clearlogo", ignoreCase = true) ||
            it.type.equals("hdtvlogo", ignoreCase = true) ||
            it.type.equals("logo", ignoreCase = true)
    }?.bestUrl
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
