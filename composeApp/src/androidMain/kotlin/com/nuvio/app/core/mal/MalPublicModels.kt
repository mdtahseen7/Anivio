package com.nuvio.app.core.mal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MalPublicAnimeNode(
    val id: Int,
    val title: String,
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
    @SerialName("alternative_titles") val alternativeTitles: MalPublicAlternativeTitles? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val synopsis: String? = null,
    @SerialName("mean") val score: Double? = null,
    val popularity: Int? = null,
    @SerialName("num_list_users") val numListUsers: Int? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val status: String? = null,
    val genres: List<MalNamedValue> = emptyList(),
    @SerialName("num_episodes") val episodeCount: Int? = null,
    @SerialName("average_episode_duration") val episodeDurationSeconds: Int? = null,
    val studios: List<MalNamedValue> = emptyList(),
    val pictures: List<MalPicture> = emptyList(),
    val season: MalSeason? = null,
)

@Serializable
data class MalPublicAnimeEntry(val node: MalPublicAnimeNode)

@Serializable
data class MalPicture(val medium: String? = null, val large: String? = null)

@Serializable
data class MalPublicAlternativeTitles(
    val synonyms: List<String> = emptyList(),
    val en: String? = null,
    val ja: String? = null,
)

@Serializable
data class MalNamedValue(val id: Int? = null, val name: String)

@Serializable
data class MalSeason(val year: Int? = null, val season: String? = null)

@Serializable
data class MalAnimePage(
    val data: List<MalPublicAnimeEntry> = emptyList(),
    val paging: MalPaging = MalPaging(),
)
