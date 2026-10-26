package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry from the external daily anime-search ranking feed. */
@Serializable
data class DailyAnimeRanking(
    val anilistId: Int,
    val animeTitle: String? = null,
    val animeTitleNative: String? = null,
    val coverImageUrl: String? = null,
    val storedThumbnailUrl: String? = null,
    val dailyCount: Int = 0,
)

/**
 * Daily ranking data intentionally comes from outside AniList. AniList is only used later to fill
 * stable display metadata and preserve the app's existing detail routes.
 */
object DailyAnimeRankingsClient {
    private const val ENDPOINT = "https://whatanimeisthis.com/api/anime/rankings"
    private const val CACHE_TTL_MS = 60L * 60 * 1000

    private val log = Logger.withTag("DailyAnimeRankingsClient")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val mutex = Mutex()
    private var cached: CachedRanking? = null

    suspend fun fetch(limit: Int = 10, forceRefresh: Boolean = false): List<DailyAnimeRanking> {
        val boundedLimit = limit.coerceIn(1, 10)
        if (!forceRefresh) {
            mutex.withLock {
                cached?.takeIf { EpisodeReleaseDatePlatform.nowEpochMs() - it.storedAtMs < CACHE_TTL_MS }
                    ?.items
                    ?.take(boundedLimit)
            }?.let { return it }
        }

        val items = runCatching {
            val payload = httpGetTextWithHeaders(
                url = "$ENDPOINT?period=daily&limit=$boundedLimit&includeAdult=false",
                headers = mapOf("Accept" to "application/json", "User-Agent" to "Anivio"),
            )
            json.decodeFromString<List<DailyAnimeRanking>>(payload)
                .filter { it.anilistId > 0 }
                .distinctBy(DailyAnimeRanking::anilistId)
                .take(boundedLimit)
        }.onFailure { error ->
            log.w(error) { "Daily anime ranking lookup failed" }
        }.getOrDefault(emptyList())

        if (items.isNotEmpty()) {
            mutex.withLock { cached = CachedRanking(items, EpisodeReleaseDatePlatform.nowEpochMs()) }
        }
        return items
    }

    private data class CachedRanking(
        val items: List<DailyAnimeRanking>,
        val storedAtMs: Long,
    )
}
