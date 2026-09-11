package com.nuvio.app.core.mal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MalPaging(
    val previous: String? = null,
    val next: String? = null,
)

@Serializable
data class MalPage<T>(
    val data: List<T> = emptyList(),
    val paging: MalPaging = MalPaging(),
)

@Serializable
data class MalUser(
    val id: Int,
    val name: String,
    val picture: String? = null,
)

@Serializable
data class MalMainPicture(
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
data class MalAlternativeTitles(
    val synonyms: List<String> = emptyList(),
    val en: String? = null,
    val ja: String? = null,
)

@Serializable
data class MalGenre(
    val id: Int? = null,
    val name: String,
)

@Serializable
data class MalAnimeNode(
    val id: Int,
    val title: String,
    @SerialName("main_picture") val mainPicture: MalMainPicture? = null,
    @SerialName("alternative_titles") val alternativeTitles: MalAlternativeTitles? = null,
    @SerialName("start_date") val startDate: String? = null,
    val mean: Double? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val status: String? = null,
    val genres: List<MalGenre> = emptyList(),
    @SerialName("num_episodes") val numEpisodes: Int = 0,
)

@Serializable
data class MalListStatus(
    val status: String,
    val score: Int = 0,
    @SerialName("num_episodes_watched") val numEpisodesWatched: Int = 0,
    @SerialName("is_rewatching") val isRewatching: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MalAnimeListEntry(
    val node: MalAnimeNode,
    @SerialName("list_status") val listStatus: MalListStatus,
)

@Serializable
data class MalTokenResponse(
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresInSeconds: Long,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
internal data class MalErrorPayload(
    val error: String? = null,
    val message: String? = null,
)

sealed class MalApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unauthorized(message: String) : MalApiException(message)
    class RateLimited(val retryAfterMs: Long?, message: String) : MalApiException(message)
    class Http(val statusCode: Int, message: String) : MalApiException(message)
    class MalformedResponse(message: String, cause: Throwable? = null) : MalApiException(message, cause)
    class ResponseTooLarge(cause: Throwable) : MalApiException("MyAnimeList response exceeded the safety limit", cause)
    class Network(cause: Throwable) : MalApiException("MyAnimeList request failed", cause)
}
