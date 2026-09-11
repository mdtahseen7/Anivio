package com.nuvio.app.core.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpGetTextWithHeaders
import com.nuvio.app.features.addons.httpPostJsonWithHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TvdbEpisode(
    val number: Int,
    val seasonNumber: Int?,
    val title: String? = null,
    val overview: String? = null,
    val imageUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int? = null,
)

/** Best-scored series artwork per slot, chosen from everything TVDB offers for the show. */
data class TvdbArtwork(
    val posterUrl: String? = null,
    val backgroundUrl: String? = null,
    val clearLogoUrl: String? = null,
)

/**
 * Minimal TheTVDB v4 client, for the two things TVDB does better than anything else in this app:
 * per-season posters, and episode lists that already carry `seasonNumber`.
 *
 * The series id comes from `api.ani.zip`, so this cannot help titles ani.zip has no mapping for.
 * What it does fix is ani.zip's frequently-null English episode titles, and it replaces the
 * inferred-then-patched TMDB season poster with the season's own key art.
 *
 * Auth is a single `POST /login` with the project key — no PIN, since this is not a user-supported
 * key — returning a bearer token good for about a month, cached here for a day.
 */
object TvdbClient {
    private const val BASE_URL = "https://api4.thetvdb.com/v4"
    private const val ARTWORK_HOST = "https://artworks.thetvdb.com"

    /** Artwork type ids from `/artwork/types`: 2 poster, 3 background, 23 clear logo. */
    private const val TYPE_POSTER = 2
    private const val TYPE_BACKGROUND = 3
    private const val TYPE_CLEARLOGO = 23

    /** TVDB seasons come in several orderings under the same number; this is the one we want. */
    private const val AIRED_ORDER = "Aired Order"

    private const val TOKEN_TTL_MS = 24 * 60 * 60 * 1000L

    /** Single-entry cache, so the key is a constant. */
    private const val TOKEN_CACHE_KEY = "tvdb_bearer"
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L
    private const val CACHE_MAX_ENTRIES = 32
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024

    private val log = Logger.withTag("TvdbClient")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val tokenMutex = Mutex()
    private var token: String? = null
    private var tokenFetchedAtMs = 0L

    private val cacheMutex = Mutex()
    private val seasonPosters = linkedMapOf<String, CachedValue<String?>>()
    private val episodeCache = linkedMapOf<String, CachedValue<Map<Int, TvdbEpisode>>>()
    private val artworkCache = linkedMapOf<String, CachedValue<TvdbArtwork>>()

    /**
     * Best series artwork TVDB has, one request for all slots.
     *
     * `api.ani.zip` exposes exactly one image per type; this endpoint returns everything — 34
     * posters, 32 backgrounds and 3 clear logos for Re:ZERO — so the pick can be by score and
     * language instead of taking whatever ani.zip chose.
     */
    suspend fun seriesArtwork(seriesId: String): TvdbArtwork {
        if (!TvdbSettingsRepository.isConfigured || seriesId.isBlank()) return TvdbArtwork()

        cacheMutex.withLock {
            artworkCache[seriesId]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }
        }?.let { return it.value }

        // Artwork is effectively immutable once published, so the disk copy is trusted for far
        // longer than the in-memory TTL. This is what stops logos being re-fetched every launch.
        TvdbArtworkDiskCache.get(seriesId, nowMs())?.let { stored ->
            cacheMutex.withLock {
                artworkCache.remove(seriesId)
                artworkCache[seriesId] = CachedValue(stored, nowMs())
                while (artworkCache.size > CACHE_MAX_ENTRIES) artworkCache.remove(artworkCache.keys.first())
            }
            return stored
        }

        val resolved = runCatching {
            val payload = authorizedGet("/series/$seriesId/artworks") ?: return@runCatching TvdbArtwork()
            val artworks = json.decodeFromString<TvdbArtworksResponse>(payload)
                .data
                ?.artworks
                .orEmpty()

            TvdbArtwork(
                // Posters carry burnt-in titles, so an English one beats a Japanese one.
                posterUrl = artworks.best(TYPE_POSTER, preferEnglish = true),
                // Backgrounds are usually textless (`language: null`), which is what we want behind
                // the app's own title treatment.
                backgroundUrl = artworks.best(TYPE_BACKGROUND, preferEnglish = false),
                clearLogoUrl = artworks.best(TYPE_CLEARLOGO, preferEnglish = true),
            )
        }.onFailure { error ->
            log.w(error) { "TVDB artwork lookup failed for series/$seriesId" }
        }.getOrDefault(TvdbArtwork())

        cacheMutex.withLock {
            artworkCache.remove(seriesId)
            artworkCache[seriesId] = CachedValue(resolved, nowMs())
            while (artworkCache.size > CACHE_MAX_ENTRIES) artworkCache.remove(artworkCache.keys.first())
        }
        TvdbArtworkDiskCache.put(seriesId, resolved, nowMs())
        return resolved
    }

    /**
     * Highest score wins within the preferred language tier. TVDB scores run in the 100000s, and
     * `language` is null for textless art.
     */
    private fun List<TvdbArtworkEntry>.best(type: Int, preferEnglish: Boolean): String? {
        val candidates = filter { it.type == type && !it.image.isNullOrBlank() }
        if (candidates.isEmpty()) return null

        val tiers = if (preferEnglish) {
            listOf({ e: TvdbArtworkEntry -> e.language == "eng" }, { e -> e.language == null }, { _ -> true })
        } else {
            listOf({ e: TvdbArtworkEntry -> e.language == null }, { e -> e.language == "eng" }, { _ -> true })
        }
        tiers.forEach { matches ->
            candidates.filter(matches)
                .maxByOrNull { it.score ?: 0L }
                ?.image
                ?.let(::absoluteArtworkUrl)
                ?.let { return it }
        }
        return null
    }

    /** Poster for one aired-order season, or null when TVDB has none. */
    suspend fun seasonPosterUrl(seriesId: String, seasonNumber: Int): String? {
        if (!TvdbSettingsRepository.isConfigured || seriesId.isBlank()) return null
        val cacheKey = "$seriesId:$seasonNumber"

        cacheMutex.withLock {
            seasonPosters[cacheKey]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }
        }?.let { return it.value }

        val resolved = runCatching {
            val payload = authorizedGet("/series/$seriesId/extended") ?: return@runCatching null
            json.decodeFromString<TvdbSeriesExtendedResponse>(payload)
                .data
                ?.seasons
                .orEmpty()
                .firstOrNull { season ->
                    season.number == seasonNumber && season.type?.name == AIRED_ORDER
                }
                ?.image
                ?.let(::absoluteArtworkUrl)
        }.onFailure { error ->
            log.w(error) { "TVDB season poster lookup failed for series/$seriesId season $seasonNumber" }
        }.getOrNull()

        cacheMutex.withLock {
            seasonPosters.remove(cacheKey)
            seasonPosters[cacheKey] = CachedValue(resolved, nowMs())
            while (seasonPosters.size > CACHE_MAX_ENTRIES) seasonPosters.remove(seasonPosters.keys.first())
        }
        return resolved
    }

    /**
     * Episodes for one season, keyed by episode number. One request returns every season, so the
     * whole series is cached and filtered here.
     */
    suspend fun episodes(seriesId: String, seasonNumber: Int): Map<Int, TvdbEpisode> {
        if (!TvdbSettingsRepository.isConfigured || seriesId.isBlank()) return emptyMap()

        val all = cachedSeriesEpisodes(seriesId)
        return all.values
            .filter { it.seasonNumber == seasonNumber }
            .associateBy(TvdbEpisode::number)
    }

    private suspend fun cachedSeriesEpisodes(seriesId: String): Map<Int, TvdbEpisode> {
        cacheMutex.withLock {
            episodeCache[seriesId]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }
        }?.let { return it.value }

        // Survives the process, so the first details screen after launch does not pay for a list it
        // already had. Promoted into memory so repeat hits skip the decode.
        TvdbEpisodesDiskCache.get(seriesId, nowMs())?.takeIf { it.isNotEmpty() }?.let { stored ->
            cacheMutex.withLock {
                episodeCache.remove(seriesId)
                episodeCache[seriesId] = CachedValue(stored, nowMs())
                while (episodeCache.size > CACHE_MAX_ENTRIES) episodeCache.remove(episodeCache.keys.first())
            }
            return stored
        }

        val resolved = runCatching {
            // The `/eng` variant is the point of coming here: ani.zip's `title.en` is null for a lot
            // of currently-airing episodes.
            val payload = authorizedGet("/series/$seriesId/episodes/official/eng?page=0")
                ?: return@runCatching emptyMap()
            json.decodeFromString<TvdbEpisodesResponse>(payload)
                .data
                ?.episodes
                .orEmpty()
                .mapNotNull { entry ->
                    val number = entry.number ?: return@mapNotNull null
                    number to TvdbEpisode(
                        number = number,
                        seasonNumber = entry.seasonNumber,
                        title = entry.name?.takeIf { it.isNotBlank() },
                        overview = entry.overview?.takeIf { it.isNotBlank() },
                        imageUrl = entry.image?.let(::absoluteArtworkUrl),
                        airDate = entry.aired?.takeIf { it.isNotBlank() },
                        runtimeMinutes = entry.runtime?.takeIf { it > 0 },
                    )
                }
                // Keyed per season below, so collisions across seasons are expected here.
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, values) -> values.first() }
        }.onFailure { error ->
            log.w(error) { "TVDB episode lookup failed for series/$seriesId" }
        }.getOrDefault(emptyMap())

        cacheMutex.withLock {
            episodeCache.remove(seriesId)
            episodeCache[seriesId] = CachedValue(resolved, nowMs())
            while (episodeCache.size > CACHE_MAX_ENTRIES) episodeCache.remove(episodeCache.keys.first())
        }
        // An empty map means the request failed or TVDB has nothing; persisting it would hide a
        // recovered mapping for a whole day.
        if (resolved.isNotEmpty()) {
            TvdbEpisodesDiskCache.put(seriesId, resolved, nowMs())
        }
        return resolved
    }

    private suspend fun authorizedGet(path: String): String? {
        val bearer = bearerToken() ?: return null
        return httpGetTextWithHeaders(
            url = "$BASE_URL$path",
            headers = mapOf(
                "Authorization" to "Bearer $bearer",
                "Accept" to "application/json",
                "User-Agent" to "Anivio",
            ),
        )
    }

    private suspend fun bearerToken(): String? = tokenMutex.withLock {
        token?.takeIf { nowMs() - tokenFetchedAtMs <= TOKEN_TTL_MS }?.let { return it }

        // TVDB tokens last a day, but holding one only in memory meant a `POST /login` on every cold
        // start — a round trip in front of the first details screen after every launch.
        TvdbTokenDiskCache.get(TOKEN_CACHE_KEY, nowMs())
            ?.takeIf { it.token.isNotBlank() }
            ?.let { stored ->
                token = stored.token
                tokenFetchedAtMs = stored.fetchedAtEpochMs
                return stored.token
            }

        val fetched = runCatching {
            val payload = httpPostJsonWithHeaders(
                url = "$BASE_URL/login",
                body = """{"apikey":"${TvdbSettingsRepository.effectiveApiKey()}"}""",
                headers = mapOf("Accept" to "application/json", "User-Agent" to "Anivio"),
            )
            json.decodeFromString<TvdbLoginResponse>(payload).data?.token?.takeIf { it.isNotBlank() }
        }.onFailure { error ->
            log.w(error) { "TVDB login failed" }
        }.getOrNull()

        token = fetched
        tokenFetchedAtMs = if (fetched == null) 0L else nowMs()
        if (fetched != null) {
            TvdbTokenDiskCache.put(
                TOKEN_CACHE_KEY,
                StoredTvdbToken(token = fetched, fetchedAtEpochMs = tokenFetchedAtMs),
                tokenFetchedAtMs,
            )
        }
        fetched
    }

    /** Season images come back absolute, episode images relative — normalise both. */
    private fun absoluteArtworkUrl(path: String): String? {
        val clean = path.trim().takeIf { it.isNotBlank() } ?: return null
        return if (clean.startsWith("http", ignoreCase = true)) clean else "$ARTWORK_HOST$clean"
    }

    /** Called when the user saves a different key, so the next call re-logs in. */
    internal fun onApiKeyChanged() {
        token = null
        tokenFetchedAtMs = 0L
        // The persisted copy belongs to the old key, so expire it rather than let it outlive the
        // change and authorise requests against the wrong account for the rest of the day.
        TvdbTokenDiskCache.put(TOKEN_CACHE_KEY, StoredTvdbToken(token = "", fetchedAtEpochMs = 0L), 0L)
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedValue<T>(val value: T, val storedAtMs: Long)
}

@Serializable
private data class TvdbLoginResponse(val data: TvdbLoginData? = null)

@Serializable
private data class TvdbLoginData(val token: String? = null)

@Serializable
private data class TvdbArtworksResponse(val data: TvdbArtworksData? = null)

@Serializable
private data class TvdbArtworksData(val artworks: List<TvdbArtworkEntry> = emptyList())

@Serializable
private data class TvdbArtworkEntry(
    val type: Int? = null,
    val image: String? = null,
    /** Three-letter code, or null for textless artwork. */
    val language: String? = null,
    val score: Long? = null,
)

@Serializable
private data class TvdbSeriesExtendedResponse(val data: TvdbSeriesExtended? = null)

@Serializable
private data class TvdbSeriesExtended(val seasons: List<TvdbSeason> = emptyList())

@Serializable
private data class TvdbSeason(
    val number: Int? = null,
    val image: String? = null,
    val type: TvdbSeasonType? = null,
)

@Serializable
private data class TvdbSeasonType(val name: String? = null)

@Serializable
private data class TvdbEpisodesResponse(val data: TvdbEpisodesData? = null)

@Serializable
private data class TvdbEpisodesData(val episodes: List<TvdbEpisodeEntry> = emptyList())

@Serializable
private data class TvdbEpisodeEntry(
    val number: Int? = null,
    @SerialName("seasonNumber") val seasonNumber: Int? = null,
    val name: String? = null,
    val overview: String? = null,
    val image: String? = null,
    val aired: String? = null,
    val runtime: Int? = null,
)
