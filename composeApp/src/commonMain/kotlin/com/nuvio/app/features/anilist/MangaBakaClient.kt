package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * MangaBaka's AniList source route adds publisher, author, and official-link metadata that AniList
 * does not always carry. It is deliberately an optional enrichment: it has no reader-page API.
 */
object MangaBakaClient {
    private const val ENDPOINT = "https://api.mangabaka.org/v1/source/anilist"
    private const val CACHE_MAX_ENTRIES = 32

    private val log = Logger.withTag("MangaBakaClient")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<Int, MangaBakaSeries?>()

    suspend fun findByAniListId(anilistId: Int): MangaBakaSeries? {
        if (anilistId <= 0) return null
        cacheMutex.withLock {
            if (cache.containsKey(anilistId)) return cache[anilistId]
        }

        val result = try {
            val payload = httpGetTextWithHeaders(
                url = "$ENDPOINT/$anilistId?with_series=true",
                headers = mapOf("Accept" to "application/json", "User-Agent" to "Anivio"),
            )
            json.decodeFromString<MangaBakaResponse>(payload).data?.series?.firstOrNull()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            log.w(error) { "MangaBaka source lookup failed for AniList $anilistId" }
            null
        }

        cacheMutex.withLock {
            cache.remove(anilistId)
            cache[anilistId] = result
            while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
        }
        return result
    }
}

@Serializable
private data class MangaBakaResponse(val data: MangaBakaData? = null)

@Serializable
private data class MangaBakaData(val series: List<MangaBakaSeries> = emptyList())

@Serializable
data class MangaBakaSeries(
    val description: String? = null,
    val status: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("total_chapters") val totalChapters: String? = null,
    @SerialName("final_volume") val finalVolume: String? = null,
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val publishers: List<MangaBakaPublisher> = emptyList(),
    @SerialName("links_v2") val links: List<MangaBakaLink> = emptyList(),
)

@Serializable
data class MangaBakaPublisher(val name: String? = null)

@Serializable
data class MangaBakaLink(
    val url: String? = null,
    @SerialName("name_display") val name: String? = null,
    val type: String? = null,
)
