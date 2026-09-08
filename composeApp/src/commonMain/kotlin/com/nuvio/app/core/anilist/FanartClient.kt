package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** A transparent title logo and a wide background, as far as fanart.tv could supply them. */
data class FanartArtwork(
    val logoUrl: String? = null,
    val backgroundUrl: String? = null,
) {
    val isEmpty: Boolean get() = logoUrl == null && backgroundUrl == null
}

/**
 * fanart.tv v3 artwork lookups.
 *
 * TV is keyed by TVDB id and movies by TMDB id — both of which `api.ani.zip` already resolves for
 * us, so unlike a title-search approach this is a single direct request per title.
 *
 * Measured coverage note: `hdtvlogo` is the well-populated TV logo array (13 entries for Attack on
 * Titan, 9 for Frieren) while `clearlogo` is nearly empty (1 and 0), so hdtvlogo is preferred.
 *
 * Silently disabled when [FanartConfig] has no key — every caller has an ani.zip fallback.
 */
object FanartClient {
    private const val ENDPOINT = "https://webservice.fanart.tv/v3"
    private const val CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    private const val MISS_TTL_MS = 24 * 60 * 60 * 1000L
    private const val CACHE_MAX_ENTRIES = 128
    private const val MAX_RESPONSE_BYTES = 1024 * 1024

    private val log = Logger.withTag("FanartClient")
    private val json = Json { ignoreUnknownKeys = true }

    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<String, CachedArtwork>()

    private val TV_LOGO_KEYS = listOf("hdtvlogo", "clearlogo")
    private val TV_BACKGROUND_KEYS = listOf("show4kbackground", "showbackground")
    private val MOVIE_LOGO_KEYS = listOf("hdmovielogo", "movielogo")
    private val MOVIE_BACKGROUND_KEYS = listOf("movie4kbackground", "moviebackground")

    suspend fun tvArtwork(tvdbId: String): FanartArtwork =
        artwork("tv/$tvdbId", TV_LOGO_KEYS, TV_BACKGROUND_KEYS)

    suspend fun movieArtwork(tmdbId: String): FanartArtwork =
        artwork("movies/$tmdbId", MOVIE_LOGO_KEYS, MOVIE_BACKGROUND_KEYS)

    private suspend fun artwork(
        path: String,
        logoKeys: List<String>,
        backgroundKeys: List<String>,
    ): FanartArtwork {
        if (!FanartConfig.isConfigured) return FanartArtwork()

        cacheMutex.withLock {
            cache[path]?.takeIf { nowMs() - it.storedAtMs <= it.ttlMs }
        }?.let { return it.artwork }

        val resolved = runCatching {
            val response = httpRequestRaw(
                method = "GET",
                url = "$ENDPOINT/$path",
                headers = mapOf(
                    "api-key" to FanartConfig.API_KEY,
                    "Accept" to "application/json",
                    "User-Agent" to "Anivio",
                ),
                body = "",
                maxResponseBodyBytes = MAX_RESPONSE_BYTES,
            )

            // 404 simply means fanart has nothing for this id — not worth logging.
            if (response.status == 404) return@runCatching FanartArtwork()
            if (response.status !in 200..299) {
                log.w { "fanart.tv $path responded ${response.status}" }
                return@runCatching FanartArtwork()
            }

            val root = json.parseToJsonElement(response.body).jsonObject
            FanartArtwork(
                logoUrl = root.bestUrl(logoKeys, preferLanguage = true),
                backgroundUrl = root.bestUrl(backgroundKeys, preferLanguage = false),
            )
        }.onFailure { error ->
            log.w(error) { "fanart.tv lookup failed for $path" }
        }.getOrDefault(FanartArtwork())

        cacheMutex.withLock {
            cache.remove(path)
            cache[path] = CachedArtwork(
                artwork = resolved,
                storedAtMs = nowMs(),
                // Retry a miss the next day; real artwork barely changes, so hold it for a week.
                ttlMs = if (resolved.isEmpty) MISS_TTL_MS else CACHE_TTL_MS,
            )
            while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
        }

        return resolved
    }

    /**
     * Walks [keys] in preference order and returns the best entry: most-liked first, then — for
     * logos, where language matters — the English one, falling back to Japanese.
     */
    private fun JsonObject.bestUrl(keys: List<String>, preferLanguage: Boolean): String? {
        keys.forEach { key ->
            val entries = (this[key] as? JsonArray)
                ?.mapNotNull { it as? JsonObject }
                ?.sortedByDescending { it.string("likes")?.toIntOrNull() ?: 0 }
                .orEmpty()
            if (entries.isEmpty()) return@forEach

            val picked = if (preferLanguage) {
                entries.firstOrNull { it.string("lang") == "en" }
                    ?: entries.firstOrNull { it.string("lang") in setOf("ja", "jp") }
                    ?: entries.first()
            } else {
                entries.first()
            }
            picked.string("url")?.let { return it }
        }
        return null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedArtwork(
        val artwork: FanartArtwork,
        val storedAtMs: Long,
        val ttlMs: Long,
    )
}
