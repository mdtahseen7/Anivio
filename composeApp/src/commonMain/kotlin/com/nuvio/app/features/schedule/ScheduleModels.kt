package com.nuvio.app.features.schedule

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** One airing episode on the schedule page. */
data class ScheduleEntry(
    /** AniList media id; combined with [isAnime] into a detail-page id. */
    val mediaId: Int,
    val title: String,
    val imageUrl: String?,
    val episodeNumber: Int?,
    /** Epoch seconds when the episode airs. */
    val airingAtEpochSec: Long,
    val isAnime: Boolean,
)

/** Pure helpers over the raw AniList GraphQL data so they can be unit-tested. */
internal object ScheduleParsing {
    fun parseAiringSchedules(data: JsonObject): List<ScheduleEntry> {
        val page = data["Page"]?.jsonObject ?: return emptyList()
        val schedules = page["airingSchedules"]?.jsonArray ?: return emptyList()
        return schedules.mapNotNull { element ->
            val node = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val media = node["media"]?.jsonObject ?: return@mapNotNull null
            val mediaId = media["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val airingAt = node["airingAt"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            val title = mediaTitle(media) ?: return@mapNotNull null
            ScheduleEntry(
                mediaId = mediaId,
                title = title,
                imageUrl = media["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull,
                episodeNumber = node["episode"]?.jsonPrimitive?.intOrNull,
                airingAtEpochSec = airingAt,
                isAnime = media["type"]?.jsonPrimitive?.contentOrNull != "MANGA",
            )
        }
    }

    private fun mediaTitle(media: JsonObject): String? {
        val title = media["title"]?.jsonObject ?: return null
        return title["userPreferred"]?.jsonPrimitive?.contentOrNull
            ?: title["english"]?.jsonPrimitive?.contentOrNull
            ?: title["romaji"]?.jsonPrimitive?.contentOrNull
            ?: title["native"]?.jsonPrimitive?.contentOrNull
    }
}
