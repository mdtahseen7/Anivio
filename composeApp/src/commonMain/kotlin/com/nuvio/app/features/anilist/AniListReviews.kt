package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListReviewConnection
import com.nuvio.app.core.anilist.stripAniListMarkup
import com.nuvio.app.features.details.AnimeReview
import kotlinx.serialization.json.JsonObject

internal fun aniListReviewMediaArgument(id: String): String? {
    parseAniListMediaId(id)?.let { return "id: $it" }
    return id.trim().takeIf { it.startsWith("mal:", ignoreCase = true) }
        ?.substringAfter(':')?.substringBefore(':')?.toIntOrNull()?.takeIf { it > 0 }
        ?.let { "idMal: $it" }
}

internal suspend fun loadAniListReviews(id: String, forceRefresh: Boolean = false): List<AnimeReview> {
    val argument = aniListReviewMediaArgument(id) ?: return emptyList()
    val data = AniListClient.query(
        query = """
            query {
                Media($argument, type: ANIME) {
                    reviews(sort: RATING_DESC, perPage: 10) {
                        nodes { id summary body(asHtml: false) score user { name avatar { large medium } } }
                    }
                }
            }
        """.trimIndent(),
        forceRefresh = forceRefresh,
    )
    val reviews = (data["Media"] as? JsonObject)?.get("reviews") as? JsonObject ?: return emptyList()
    return AniListClient.json.decodeFromJsonElement(AniListReviewConnection.serializer(), reviews).toAnimeReviews()
}

internal fun AniListReviewConnection?.toAnimeReviews(): List<AnimeReview> =
    this?.nodes.orEmpty().mapNotNull { node ->
        val summary = node.summary?.stripAniListMarkup()?.trim()?.takeIf(String::isNotBlank)
        val body = node.body?.stripAniListMarkup()?.trim()?.takeIf(String::isNotBlank) ?: summary
        if (body == null) return@mapNotNull null
        AnimeReview(
            id = node.id?.toString() ?: return@mapNotNull null,
            author = node.user?.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
            avatar = node.user.avatar?.large ?: node.user.avatar?.medium,
            score = node.score?.takeIf { it in 1..100 },
            summary = summary ?: body.lineSequence().first().take(180),
            body = body,
        )
    }
