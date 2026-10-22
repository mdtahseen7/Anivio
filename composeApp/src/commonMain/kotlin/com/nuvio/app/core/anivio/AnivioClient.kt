package com.nuvio.app.core.anivio

import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

/** Small, bounded unauthenticated client for Anivio's GraphQL and mapping APIs. */
object AnivioClient {
    const val BASE_URL = "https://anivio-api.eu.cc"
    const val GRAPHQL_URL = "$BASE_URL/graphql"

    /** Public query limits: Anivio's unreliable pageInfo is intentionally never requested. */
    const val MAX_LIST_ITEMS = 20
    const val REQUEST_TIMEOUT_MS = 8_000L

    private const val CACHE_TTL_MS = 5 * 60 * 1000L
    private const val CACHE_MAX_ENTRIES = 64
    private const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024

    private val cacheMutex = Mutex()
    private val cache = linkedMapOf<String, CachedValue>()
    private val inflightMutex = Mutex()
    private val inflight = mutableMapOf<String, CompletableDeferred<Any?>>()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Existing artwork-compatible API. Core metadata and Anivio artwork are fetched together. */
    suspend fun mediaByAniListId(anilistId: Int, forceRefresh: Boolean = false): AnivioMedia? {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("media:$anilistId", forceRefresh) { requestMedia(anilistId) }
    }

    /** Alias for future fallback providers that want an explicit core-detail name. */
    suspend fun mediaDetailsByAniListId(anilistId: Int, forceRefresh: Boolean = false): AnivioMedia? =
        mediaByAniListId(anilistId, forceRefresh)

    suspend fun mediaByAniListIdOrNull(anilistId: Int, forceRefresh: Boolean = false): AnivioMedia? =
        try {
            mediaByAniListId(anilistId, forceRefresh)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            null
        }

    /** Lightweight catalog page. The server receives exactly 20 as its page size. */
    suspend fun catalog(query: AnivioCatalogQuery = AnivioCatalogQuery(), forceRefresh: Boolean = false): List<AnivioMedia> {
        val page = query.page.coerceAtLeast(1)
        val key = "catalog:$page:${query.sort}:${query.statusIn}:${query.season}:${query.seasonYear}:${query.genre}:${query.averageScoreGreater}"
        return cached(key, forceRefresh) {
            requestCatalog(query.copy(page = page)).take(MAX_LIST_ITEMS)
        }
    }

    suspend fun search(query: String, page: Int = 1, forceRefresh: Boolean = false): List<AnivioMedia> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return emptyList()
        val safePage = page.coerceAtLeast(1)
        return cached("search:$safePage:$normalized", forceRefresh) {
            requestSearch(normalized, safePage).take(MAX_LIST_ITEMS)
        }
    }

    /** Heavy detail fields are opt-in and never included in catalog/core metadata queries. */
    suspend fun mediaExtrasByAniListId(anilistId: Int, forceRefresh: Boolean = false): AnivioMediaExtras? {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("extras:$anilistId", forceRefresh) { requestMediaExtras(anilistId) }
    }

    suspend fun charactersByAniListId(anilistId: Int, forceRefresh: Boolean = false): List<AnivioCharacterEdge> {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("characters:$anilistId", forceRefresh) {
            requestCharacters(anilistId).take(MAX_LIST_ITEMS)
        }
    }

    suspend fun relationsByAniListId(anilistId: Int, forceRefresh: Boolean = false): List<AnivioRelationEdge> {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("relations:$anilistId", forceRefresh) {
            requestRelations(anilistId).take(MAX_LIST_ITEMS)
        }
    }

    suspend fun videosByAniListId(anilistId: Int, forceRefresh: Boolean = false): List<AnivioVideo> {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("videos:$anilistId", forceRefresh) {
            requestVideos(anilistId).take(MAX_LIST_ITEMS)
        }
    }

    suspend fun screenshotsByAniListId(anilistId: Int, forceRefresh: Boolean = false): List<AnivioScreenshot> {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("screenshots:$anilistId", forceRefresh) {
            requestScreenshots(anilistId).take(MAX_LIST_ITEMS)
        }
    }

    /**
     * Episode lists are intentionally not truncated client-side. The GraphQL field has no server-side
     * pagination, and truncating here would make long-running shows appear to stop at episode 20.
     * Details UI must render the returned list lazily; the response/body cap still protects the client.
     */
    suspend fun episodesByAniListId(anilistId: Int, forceRefresh: Boolean = false): List<AnivioEpisode> {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("episodes:$anilistId", forceRefresh) { requestEpisodes(anilistId) }
    }

    /** REST mapping lookup; episode mappings are retained so long-running shows remain complete. */
    suspend fun mappingByAniListId(anilistId: Int, forceRefresh: Boolean = false): AnivioMapping? {
        require(anilistId > 0) { "AniList ID must be positive" }
        return cached("mapping:$anilistId", forceRefresh) { requestMapping(anilistId) }
    }

    suspend fun clearCache() = cacheMutex.withLock { cache.clear() }

    private suspend fun requestMedia(anilistId: Int): AnivioMedia? =
        graphQl<MediaData>(MEDIA_QUERY, buildJsonObject { put("id", anilistId) })?.media

    private suspend fun requestSearch(search: String, page: Int): List<AnivioMedia> =
        graphQl<PageData>(SEARCH_QUERY, buildJsonObject {
            put("page", page)
            put("search", search)
        })?.media.orEmpty()

    private suspend fun requestCatalog(query: AnivioCatalogQuery): List<AnivioMedia> =
        graphQl<PageData>(CATALOG_QUERY, buildJsonObject {
            put("page", query.page)
            put("sort", query.sort.wireName)
            if (query.statusIn.isNotEmpty()) {
                put("statusIn", buildJsonArray { query.statusIn.forEach { add(JsonPrimitive(it)) } })
            }
            query.season?.let { put("season", it) }
            query.seasonYear?.let { put("seasonYear", it) }
            query.genre?.let { put("genre", it) }
            query.averageScoreGreater?.let { put("averageScoreGreater", it) }
        })?.media.orEmpty()

    private suspend fun requestMediaExtras(anilistId: Int): AnivioMediaExtras? =
        graphQl<ExtrasData>(EXTRAS_QUERY, buildJsonObject { put("id", anilistId) })?.media?.let {
            AnivioMediaExtras(
                characters = it.characters.edges.take(MAX_LIST_ITEMS),
                relations = it.relations.edges
                    .filter { edge -> edge.relationType?.uppercase() in setOf("PREQUEL", "SEQUEL") }
                    .take(MAX_LIST_ITEMS),
                recommendations = it.recommendations.take(MAX_LIST_ITEMS),
                videos = it.videos.take(MAX_LIST_ITEMS),
                screenshots = it.screenshots.take(MAX_LIST_ITEMS),
            )
        }

    private suspend fun requestCharacters(anilistId: Int): List<AnivioCharacterEdge> =
        graphQl<CharacterData>(CHARACTERS_QUERY, buildJsonObject { put("id", anilistId) })
            ?.media?.characters?.edges.orEmpty()

    private suspend fun requestRelations(anilistId: Int): List<AnivioRelationEdge> =
        graphQl<RelationsData>(RELATIONS_QUERY, buildJsonObject { put("id", anilistId) })
            ?.media?.relations?.edges.orEmpty()

    private suspend fun requestVideos(anilistId: Int): List<AnivioVideo> =
        graphQl<VideosData>(VIDEOS_QUERY, buildJsonObject { put("id", anilistId) })
            ?.media?.videos.orEmpty()

    private suspend fun requestScreenshots(anilistId: Int): List<AnivioScreenshot> =
        graphQl<ScreenshotsData>(SCREENSHOTS_QUERY, buildJsonObject { put("id", anilistId) })
            ?.media?.screenshots.orEmpty()

    private suspend fun requestEpisodes(anilistId: Int): List<AnivioEpisode> =
        graphQl<EpisodesData>(EPISODES_QUERY, buildJsonObject { put("id", anilistId) })
            ?.media?.episodeList.orEmpty()

    private suspend fun requestMapping(anilistId: Int): AnivioMapping? {
        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            httpRequestRaw(
                method = "GET",
                url = "$BASE_URL/mappings/$anilistId",
                headers = mapOf("Accept" to "application/json", "User-Agent" to "Anivio"),
                body = "",
                maxResponseBodyBytes = MAX_RESPONSE_BYTES,
            )
        }
        if (response.status == 404) return null
        if (response.status !in 200..299) error("Anivio mapping request failed (${response.status})")
        val envelope = AniListClient.json.decodeFromString<RestEnvelope<AnivioMapping>>(response.body)
        if (!envelope.success) error(envelope.message ?: "Anivio mapping request failed")
        return envelope.data
    }

    private suspend inline fun <reified T> graphQl(query: String, variables: JsonObject): T? {
        val response = withTimeout(REQUEST_TIMEOUT_MS) {
            httpRequestRaw(
                method = "POST",
                url = GRAPHQL_URL,
                headers = mapOf("Content-Type" to "application/json", "Accept" to "application/json", "User-Agent" to "Anivio"),
                body = buildJsonObject {
                    put("query", query)
                    put("variables", variables)
                }.toString(),
                maxResponseBodyBytes = MAX_RESPONSE_BYTES,
            )
        }
        if (response.status !in 200..299) error("Anivio API responded ${response.status}: ${response.body.take(300)}")
        val envelope = AniListClient.json.decodeFromString<GraphQlResponse<T>>(response.body)
        if (envelope.errors.isNotEmpty() && envelope.data == null) {
            error(envelope.errors.firstOrNull()?.message ?: "Anivio GraphQL request failed")
        }
        return envelope.data
    }

    private suspend inline fun <reified T> cached(
        key: String,
        forceRefresh: Boolean,
        crossinline request: suspend () -> T,
    ): T {
        if (!forceRefresh) {
            cacheMutex.withLock { cache[key]?.takeIf { nowMs() - it.storedAtMs <= CACHE_TTL_MS }?.value }
                ?.let { @Suppress("UNCHECKED_CAST") return it as T }
        }
        val deferred = inflightMutex.withLock {
            inflight[key] ?: CompletableDeferred<Any?>().also { created ->
                inflight[key] = created
                requestScope.launch {
                    try {
                        val value = request()
                        cacheMutex.withLock {
                            cache.remove(key)
                            cache[key] = CachedValue(value, nowMs())
                            while (cache.size > CACHE_MAX_ENTRIES) cache.remove(cache.keys.first())
                        }
                        created.complete(value)
                    } catch (error: Throwable) {
                        created.completeExceptionally(error)
                    } finally {
                        inflightMutex.withLock { if (inflight[key] === created) inflight.remove(key) }
                    }
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return deferred.await() as T
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

    private data class CachedValue(val value: Any?, val storedAtMs: Long)

    @Serializable private data class GraphQlResponse<T>(val data: T? = null, val errors: List<GraphQlError> = emptyList())
    @Serializable private data class GraphQlError(val message: String? = null)
    @Serializable private data class MediaData(@SerialName("Media") val media: AnivioMedia? = null)
    @Serializable private data class PageData(@SerialName("Page") val page: PagePayload? = null) {
        val media: List<AnivioMedia> get() = page?.media.orEmpty()
    }
    @Serializable private data class PagePayload(val media: List<AnivioMedia> = emptyList())
    @Serializable private data class ExtrasData(@SerialName("Media") val media: ExtrasPayload? = null)
    @Serializable private data class ExtrasPayload(
        val characters: CharacterConnection = CharacterConnection(),
        val relations: RelationConnection = RelationConnection(),
        val recommendations: List<AnivioMedia> = emptyList(),
        val videos: List<AnivioVideo> = emptyList(),
        val screenshots: List<AnivioScreenshot> = emptyList(),
    )
    @Serializable private data class CharacterConnection(val edges: List<AnivioCharacterEdge> = emptyList())
    @Serializable private data class RelationConnection(val edges: List<AnivioRelationEdge> = emptyList())
    @Serializable private data class EpisodesData(@SerialName("Media") val media: EpisodePayload? = null)
    @Serializable private data class EpisodePayload(val episodeList: List<AnivioEpisode> = emptyList())
    @Serializable private data class CharacterData(@SerialName("Media") val media: CharacterPayload? = null)
    @Serializable private data class CharacterPayload(val characters: CharacterConnection = CharacterConnection())
    @Serializable private data class RelationsData(@SerialName("Media") val media: RelationsPayload? = null)
    @Serializable private data class RelationsPayload(val relations: RelationConnection = RelationConnection())
    @Serializable private data class VideosData(@SerialName("Media") val media: VideosPayload? = null)
    @Serializable private data class VideosPayload(val videos: List<AnivioVideo> = emptyList())
    @Serializable private data class ScreenshotsData(@SerialName("Media") val media: ScreenshotsPayload? = null)
    @Serializable private data class ScreenshotsPayload(val screenshots: List<AnivioScreenshot> = emptyList())
    @Serializable private data class RestEnvelope<T>(val success: Boolean = false, val data: T? = null, val message: String? = null)

    private const val CORE_FIELDS = """
        id idMal title { romaji english native } type format status description
        startDate { year month day } endDate { year month day } source hashtag
        episodes duration season seasonYear averageScore popularity trending favourites
        countryOfOrigin isAdult genres tags { id name description category rank isMediaSpoiler isAdult }
        externalLinks { url site type } studios(isMain: true) { nodes { id name isMain } }
        nextAiringEpisode { episode airingAt timeUntilAiring mediaId }
        stats { scoreDistribution { score amount } statusDistribution { status amount } }
        siteUrl
        coverImage { extraLarge large medium color } bannerImage
        images { id url small medium large type source }
        artworks { id url large medium type source width height language isAdult }
    """

    /** Deliberately small selection for list/search pages; no artwork galleries or heavy connections. */
    private const val LIST_FIELDS = """
        id idMal title { romaji english native } type format status seasonYear averageScore episodes
        coverImage { large medium color }
    """

    private const val MEDIA_QUERY = """
        query MediaByAniListId(${"$"}id: Int) {
          Media(id: ${"$"}id) { $CORE_FIELDS }
        }
    """

    private const val SEARCH_QUERY = """
        query Search(${"$"}page: Int!, ${"$"}search: String) {
          Page(page: ${"$"}page, perPage: 20) {
            media(type: ANIME, search: ${"$"}search, sort: [POPULARITY_DESC]) { $LIST_FIELDS }
          }
        }
    """

    private const val CATALOG_QUERY = """
        query Catalog(${"$"}page: Int!, ${"$"}sort: MediaSort!, ${"$"}statusIn: [MediaStatus], ${"$"}season: MediaSeason, ${"$"}seasonYear: Int, ${"$"}genre: String, ${"$"}averageScoreGreater: Int) {
          Page(page: ${"$"}page, perPage: 20) {
            media(type: ANIME, sort: [${"$"}sort], status_in: ${"$"}statusIn, season: ${"$"}season, seasonYear: ${"$"}seasonYear, genre: ${"$"}genre, averageScore_greater: ${"$"}averageScoreGreater) { $LIST_FIELDS }
          }
        }
    """

    private const val EXTRAS_QUERY = """
        query MediaExtras(${"$"}id: Int) {
          Media(id: ${"$"}id) {
            characters { edges { role node { id name { first middle last full native alternative alternativeSpoiler } image { large medium } description gender dateOfBirth { year month day } age bloodType siteUrl } voiceActors { id name { first middle last full native alternative } image { large medium } siteUrl } } }
            relations { edges { relationType node { id title { romaji english native } type format status seasonYear averageScore popularity isAdult coverImage { large medium } } } }
            recommendations { id title { romaji english native } type format status seasonYear averageScore popularity isAdult coverImage { large medium } }
            videos { id url title thumbnail artist type source }
            screenshots { id url small medium large order source }
          }
        }
    """

    private const val EPISODES_QUERY = """
        query MediaEpisodes(${"$"}id: Int) {
          Media(id: ${"$"}id) { episodeList { id number title overview airDate runtime image { small medium large } } }
        }
    """

    private const val CHARACTERS_QUERY = """
        query MediaCharacters(${"$"}id: Int) {
          Media(id: ${"$"}id) {
            characters { edges { role node { id name { first middle last full native alternative alternativeSpoiler } image { large medium } } voiceActors { id name { first middle last full native alternative } image { large medium } } } }
          }
        }
    """

    private const val RELATIONS_QUERY = """
        query MediaRelations(${"$"}id: Int) {
          Media(id: ${"$"}id) {
            relations { edges { relationType node { id title { romaji english native } coverImage { large medium } seasonYear } } }
          }
        }
    """

    private const val VIDEOS_QUERY = """
        query MediaVideos(${"$"}id: Int) {
          Media(id: ${"$"}id) { videos { id url title thumbnail artist type source } }
        }
    """

    private const val SCREENSHOTS_QUERY = """
        query MediaScreenshots(${"$"}id: Int) {
          Media(id: ${"$"}id) { screenshots { id url small medium large order source } }
        }
    """
}
