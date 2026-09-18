package com.nuvio.app.features.player.skip

import com.nuvio.app.core.anilist.AniZipClient
import com.nuvio.app.core.anilist.mappedSeasonNumber
import com.nuvio.app.core.anilist.mappedType
import com.nuvio.app.core.anilist.tmdbId
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object SkipIntroRepository {

    private val cache = HashMap<String, List<SkipInterval>>()
    private val imdbEntriesCache = HashMap<String, List<ArmEntry>>()
    private val animeSkipShowIdCache = HashMap<String, String>()
    private val anizipCache = HashMap<String, com.nuvio.app.core.anilist.AniZipResponse?>()
    private const val NO_ID = "__none__"

    private val introDbConfigured: Boolean
        get() = IntroDbConfig.URL.isNotBlank()

    /** TheIntroDB (theintrodb.org) requires a user API key for both reads and submits. */
    private fun theIntroDbApiKey(): String =
        PlayerSettingsRepository.uiState.value.theIntroDbApiKey.trim()

    suspend fun getSkipIntervals(
        imdbId: String?,
        season: Int,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
    ): List<SkipInterval> = coroutineScope {
        if (imdbId == null) return@coroutineScope emptyList()
        val settings = PlayerSettingsRepository.uiState.value
        if (requireSkipIntroEnabled && !settings.skipIntroEnabled) return@coroutineScope emptyList()

        val cacheKey = "$imdbId:$season:$episode"
        cache[cacheKey]?.let { return@coroutineScope it }

        val theIntroDbDeferred = async { fetchFromTheIntroDbViaImdb(imdbId, season, episode) }
        val introDbDeferred = async {
            if (introDbConfigured) fetchFromIntroDb(imdbId, season, episode) else emptyList()
        }
        val entriesDeferred = async { resolveImdbEntries(imdbId) }
        val entries = entriesDeferred.await()
        val animeSkipDeferred = async { fetchAnimeSkipForEntries(entries, season, episode) }
        val malId = entries.getOrNull(season - 1)?.myanimelist?.toString()
            ?: entries.firstOrNull()?.myanimelist?.toString()
        val aniSkipDeferred = async {
            if (malId != null) fetchFromAniSkip(malId, episode) else emptyList()
        }

        return@coroutineScope mergeByPriority(
            theIntroDbDeferred.await(),
            introDbDeferred.await(),
            animeSkipDeferred.await(),
            aniSkipDeferred.await(),
        ).also { cache[cacheKey] = it }
    }

    suspend fun getSkipIntervalsForMal(
        malId: String,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
    ): List<SkipInterval> = coroutineScope {
        val settings = PlayerSettingsRepository.uiState.value
        if (requireSkipIntroEnabled && !settings.skipIntroEnabled) return@coroutineScope emptyList()

        val cacheKey = "mal:$malId:$episode"
        cache[cacheKey]?.let { return@coroutineScope it }

        val aniSkipDeferred = async { fetchFromAniSkip(malId, episode) }

        val imdbIdDeferred = async {
            try {
                SkipIntroApi.resolveMalToImdb(malId)?.imdb
            } catch (_: Exception) { null }
        }
        val anizipDeferred = async { anizipMappingsByMal(malId) }

        var introDb = emptyList<SkipInterval>()
        var animeSkip = emptyList<SkipInterval>()
        var theIntroDb: List<SkipInterval> = anizipDeferred.await()
            ?.let { anizip ->
                fetchFromTheIntroDb(
                    tmdbId = anizip.tmdbId(),
                    isMovie = anizip.mappedType().equals("MOVIE", ignoreCase = true),
                    season = anizip.mappedSeasonNumber() ?: seasonFallback(emptyList(), malId),
                    episode = episode,
                    requireKey = true,
                )
            }
            ?: emptyList()
        val imdbId = imdbIdDeferred.await()
        if (imdbId != null) {
            val entries = resolveImdbEntries(imdbId)
            val season = entries.indexOfFirst { it.myanimelist == malId.toIntOrNull() } + 1
            val introDbDeferred = async {
                if (introDbConfigured) fetchFromIntroDb(imdbId, season, episode) else emptyList()
            }
            val animeSkipDeferred = async { fetchAnimeSkipForEntries(entries, season, episode) }
            introDb = introDbDeferred.await()
            animeSkip = animeSkipDeferred.await()
        } else {
            val anilistId = try {
                SkipIntroApi.resolveMalToAnilist(malId)?.anilist?.toString()
            } catch (_: Exception) { null }
            if (anilistId != null) animeSkip = fetchFromAnimeSkip(anilistId, episode, season = null)
        }

        return@coroutineScope mergeByPriority(theIntroDb, introDb, animeSkip, aniSkipDeferred.await()).also { cache[cacheKey] = it }
    }

    suspend fun getSkipIntervalsForKitsu(
        kitsuId: String,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
    ): List<SkipInterval> = coroutineScope {
        val settings = PlayerSettingsRepository.uiState.value
        if (requireSkipIntroEnabled && !settings.skipIntroEnabled) return@coroutineScope emptyList()

        val cacheKey = "kitsu:$kitsuId:$episode"
        cache[cacheKey]?.let { return@coroutineScope it }

        val malIdDeferred = async {
            try {
                SkipIntroApi.resolveKitsuToMal(kitsuId)?.myanimelist?.toString()
            } catch (_: Exception) { null }
        }
        val imdbIdDeferred = async {
            try {
                SkipIntroApi.resolveKitsuToImdb(kitsuId)?.imdb
            } catch (_: Exception) { null }
        }
        val aniSkipDeferred = async {
            malIdDeferred.await()?.let { fetchFromAniSkip(it, episode) } ?: emptyList()
        }

        var introDb = emptyList<SkipInterval>()
        var animeSkip = emptyList<SkipInterval>()
        var theIntroDb = emptyList<SkipInterval>()
        val imdbId = imdbIdDeferred.await()
        if (imdbId != null) {
            theIntroDb = fetchFromTheIntroDbViaImdb(imdbId, episode, episode)
            val entries = resolveImdbEntries(imdbId)
            val season = entries.indexOfFirst { it.kitsu == kitsuId.toIntOrNull() } + 1
            val introDbDeferred = async {
                if (introDbConfigured) fetchFromIntroDb(imdbId, season, episode) else emptyList()
            }
            val animeSkipDeferred = async { fetchAnimeSkipForEntries(entries, season, episode) }
            introDb = introDbDeferred.await()
            animeSkip = animeSkipDeferred.await()
        } else {
            val anilistId = try {
                SkipIntroApi.resolveKitsuToAnilist(kitsuId)?.anilist?.toString()
            } catch (_: Exception) { null }
            if (anilistId != null) animeSkip = fetchFromAnimeSkip(anilistId, episode, season = null)
        }

        return@coroutineScope mergeByPriority(theIntroDb, introDb, animeSkip, aniSkipDeferred.await()).also { cache[cacheKey] = it }
    }

    /** Fetches skip intervals for an AniList-sourced item (`anilist:21:1:5`). */
    suspend fun getSkipIntervalsForAnilist(
        anilistId: String,
        season: Int,
        episode: Int,
        requireSkipIntroEnabled: Boolean = true,
    ): List<SkipInterval> = coroutineScope {
        val settings = PlayerSettingsRepository.uiState.value
        if (requireSkipIntroEnabled && !settings.skipIntroEnabled) return@coroutineScope emptyList()

        val cacheKey = "anilist:$anilistId:$episode"
        cache[cacheKey]?.let { return@coroutineScope it }

        val aniSkipDeferred = async {
            try {
                SkipIntroApi.resolveAnilistToMal(anilistId)?.myanimelist?.toString()
            } catch (_: Exception) { null }
        }?.let { malDeferred ->
            async { malDeferred.await()?.let { fetchFromAniSkip(it, episode) } ?: emptyList() }
        }

        val anizip = anizipMappingsByAnilist(anilistId)
        val theIntroDb = anizip?.let { mappings ->
            fetchFromTheIntroDb(
                tmdbId = mappings.tmdbId(),
                isMovie = mappings.mappedType().equals("MOVIE", ignoreCase = true),
                season = mappings.mappedSeasonNumber() ?: season,
                episode = episode,
                requireKey = true,
            )
        } ?: emptyList()

        val animeSkip = if (anizip != null) {
            fetchAnimeSkipForAnilist(anilistId, season, episode)
        } else {
            emptyList()
        }

        return@coroutineScope mergeByPriority(
            theIntroDb,
            animeSkip,
            aniSkipDeferred?.await() ?: emptyList(),
        ).also { cache[cacheKey] = it }
    }

    private suspend fun anizipMappingsByAnilist(anilistId: String) =
        anizipCache["anilist:$anilistId"].let { cached ->
            if (cached != null || anizipCache.containsKey("anilist:$anilistId")) {
                cached
            } else {
                val id = anilistId.toIntOrNull()
                try {
                    AniZipClient.mappings(id ?: return null)
                } catch (_: Exception) {
                    null
                }.also { anizipCache["anilist:$anilistId"] = it }
            }
        }

    private suspend fun anizipMappingsByMal(malId: String) =
        anizipCache["mal:$malId"].let { cached ->
            if (cached != null || anizipCache.containsKey("mal:$malId")) {
                cached
            } else {
                val id = malId.toIntOrNull()
                try {
                    AniZipClient.mappingsByMalId(id ?: return null)
                } catch (_: Exception) {
                    null
                }.also { anizipCache["mal:$malId"] = it }
            }
        }

    private suspend fun seasonFallback(entries: List<ArmEntry>, malId: String): Int? {
        if (entries.isEmpty()) return null
        val index = entries.indexOfFirst { it.myanimelist == malId.toIntOrNull() }
        return if (index >= 0) index + 1 else null
    }

    /**
     * TheIntroDB v3 read. The user's API key gates this call — without a key the provider is
     * skipped entirely (that is how theintrodb.org is designed; reads are not anonymous).
     */
    private suspend fun fetchFromTheIntroDb(
        tmdbId: String?,
        isMovie: Boolean,
        season: Int?,
        episode: Int?,
        requireKey: Boolean,
        durationMs: Long? = null,
    ): List<SkipInterval> {
        val apiKey = theIntroDbApiKey()
        if (apiKey.isBlank()) return emptyList()
        if (tmdbId.isNullOrBlank()) return emptyList()
        return try {
            val data = SkipIntroApi.getTheIntroDbSegments(
                tmdbId = tmdbId,
                isMovie = isMovie,
                season = season,
                episode = episode,
                durationMs = durationMs,
            ) ?: return emptyList()
            listOfNotNull(
                data.intro.firstPositiveSpanOrNull("intro"),
                data.recap.firstPositiveSpanOrNull("recap"),
                data.credits.firstPositiveSpanOrNull("outro"),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * TheIntroDB lookup for IMDb-sourced items. IMDb ids aren't accepted by v3, so resolve a
     * TMDB id through the user's TMDB settings (best-effort) — failing that, skip the provider.
     */
    private suspend fun fetchFromTheIntroDbViaImdb(
        imdbId: String,
        season: Int,
        episode: Int,
    ): List<SkipInterval> {
        val apiKey = theIntroDbApiKey()
        if (apiKey.isBlank()) return emptyList()
        val tmdbId = try {
            TmdbService.ensureTmdbId(imdbId, "tv")
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return fetchFromTheIntroDb(
            tmdbId = tmdbId,
            isMovie = false,
            season = season,
            episode = episode,
            requireKey = true,
        )
    }

    private fun List<TheIntroDbSpan>.firstPositiveSpanOrNull(type: String): SkipInterval? {
        val span = firstOrNull { s ->
            val start = s.startMs ?: 0L
            val end = s.endMs ?: Long.MAX_VALUE
            end > start
        } ?: return null
        val startSec = (span.startMs ?: 0L) / 1000.0
        val endMs = span.endMs ?: return null
        if (endMs <= (span.startMs ?: 0L)) return null
        return SkipInterval(startTime = startSec, endTime = endMs / 1000.0, type = type, provider = "theintrodb")
    }

    private suspend fun fetchAnimeSkipForAnilist(anilistId: String, season: Int, episode: Int): List<SkipInterval> {
        val seasonAnilistId = anilistId
        val fallbackAnilistId = anilistId
        for ((candidateId, seasonFilter) in listOfNotNull(
            seasonAnilistId to null,
            if (fallbackAnilistId != seasonAnilistId) fallbackAnilistId to season else null,
        )) {
            val result = fetchFromAnimeSkip(candidateId, episode, season = seasonFilter)
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    /**
     * Merge provider results into one best-of: fill each segment category (opening / ending /
     * recap) from the highest-priority provider that has it. Arguments MUST be passed in priority
     * order (IntroDB has the broadest coverage, then Anime-Skip, then AniSkip), so a partial
     * result from one provider never shadows a complete segment from another.
     */
    private fun mergeByPriority(vararg providerResults: List<SkipInterval>): List<SkipInterval> {
        val chosen = LinkedHashMap<String, SkipInterval>()
        for (result in providerResults) {
            for (interval in result) {
                val category = segmentCategory(interval.type) ?: continue
                if (category !in chosen) chosen[category] = interval
            }
        }
        return chosen.values.toList()
    }

    private fun segmentCategory(type: String): String? = when (type.lowercase()) {
        "intro", "op", "mixed-op" -> "opening"
        "outro", "ed", "mixed-ed", "credits", "ending" -> "ending"
        "recap" -> "recap"
        else -> null
    }

    // AnimeSkip: season-specific AniList ID first, then season-1 as a season-filtered fallback.
    private suspend fun fetchAnimeSkipForEntries(
        entries: List<ArmEntry>,
        season: Int,
        episode: Int
    ): List<SkipInterval> {
        val seasonAnilistId = entries.getOrNull(season - 1)?.anilist?.toString()
        val fallbackAnilistId = entries.firstOrNull()?.anilist?.toString()
        for ((anilistId, seasonFilter) in listOfNotNull(
            seasonAnilistId?.let { it to null },
            if (fallbackAnilistId != null && fallbackAnilistId != seasonAnilistId) fallbackAnilistId to season else null
        )) {
            val result = fetchFromAnimeSkip(anilistId, episode, season = seasonFilter)
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private suspend fun fetchFromIntroDb(imdbId: String, season: Int, episode: Int): List<SkipInterval> {
        return try {
            val data = SkipIntroApi.getIntroDbSegments(imdbId, season, episode)
            if (data == null) return emptyList()
            listOfNotNull(
                data.intro.toSkipIntervalOrNull("intro"),
                data.recap.toSkipIntervalOrNull("recap"),
                data.outro.toSkipIntervalOrNull("outro"),
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun IntroDbSegment?.toSkipIntervalOrNull(type: String): SkipInterval? {
        if (this == null) return null
        val start = startSec ?: startMs?.let { it / 1000.0 }
        val end = endSec ?: endMs?.let { it / 1000.0 }
        if (start == null || end == null || end <= start) return null
        return SkipInterval(startTime = start, endTime = end, type = type, provider = "introdb")
    }

    private suspend fun fetchFromAniSkip(malId: String, episode: Int): List<SkipInterval> {
        return try {
            val response = SkipIntroApi.getAniSkipTimes(malId, episode)
            if (response == null) return emptyList()
            if (!response.found) return emptyList()
            response.results?.map { result ->
                SkipInterval(
                    startTime = result.interval.startTime,
                    endTime = result.interval.endTime,
                    type = result.skipType,
                    provider = "aniskip",
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchFromAnimeSkip(anilistId: String, episode: Int, season: Int?): List<SkipInterval> {
        val settings = PlayerSettingsRepository.uiState.value
        val clientId = settings.animeSkipClientId.trim()
        if (clientId.isBlank()) return emptyList()
        if (!settings.animeSkipEnabled) return emptyList()

        return try {
            val showIds = resolveAnimeSkipShowIds(anilistId, clientId)
            if (showIds.isEmpty()) return emptyList()

            for (showId in showIds) {
                val query = "{ findEpisodesByShowId(showId: \"$showId\") { season number timestamps { at type { name } } } }"
                val response = SkipIntroApi.queryAnimeSkip(clientId, query) ?: continue
                val episodes = response.data?.findEpisodesByShowId ?: continue

                val targetEpisode = episodes.firstOrNull { ep ->
                    ep.number?.toIntOrNull() == episode &&
                        (season == null || ep.season?.toIntOrNull() == season)
                } ?: continue

                val sorted = (targetEpisode.timestamps ?: continue).sortedBy { it.at }
                val result = sorted.mapIndexedNotNull { i, ts ->
                    val endTime = sorted.getOrNull(i + 1)?.at ?: Double.MAX_VALUE
                    val type = when (ts.type.name.lowercase()) {
                        "intro", "new intro" -> "op"
                        "credits" -> "ed"
                        "recap" -> "recap"
                        else -> return@mapIndexedNotNull null
                    }
                    SkipInterval(startTime = ts.at, endTime = endTime, type = type, provider = "animeskip")
                }
                if (result.isNotEmpty()) return result
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun resolveAnimeSkipShowIds(anilistId: String, clientId: String): List<String> {
        animeSkipShowIdCache[anilistId]?.let { cached ->
            return if (cached == NO_ID) emptyList() else listOf(cached)
        }
        val query = "{ findShowsByExternalId(service: ANILIST, serviceId: \"$anilistId\") { id } }"
        val showIds = try {
            SkipIntroApi.queryAnimeSkip(clientId, query)
                ?.data?.findShowsByExternalId?.map { it.id } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        if (showIds.size == 1) animeSkipShowIdCache[anilistId] = showIds[0]
        else if (showIds.isEmpty()) animeSkipShowIdCache[anilistId] = NO_ID
        return showIds
    }

    private suspend fun resolveImdbEntries(imdbId: String): List<ArmEntry> {
        imdbEntriesCache[imdbId]?.let { return it }
        return try {
            SkipIntroApi.resolveImdbToAll(imdbId)
        } catch (_: Exception) { emptyList() }.also { imdbEntriesCache[imdbId] = it }
    }

    suspend fun submitIntro(
        imdbId: String,
        season: Int,
        episode: Int,
        startSec: Double,
        endSec: Double,
        segmentType: String,
    ): Boolean {
        val settings = PlayerSettingsRepository.uiState.value
        val apiKey = settings.introDbApiKey.trim()
        if (!settings.introSubmitEnabled || apiKey.isBlank()) return false

        val request = SubmitIntroRequest(
            imdbId = imdbId,
            season = season,
            episode = episode,
            startSec = startSec,
            endSec = endSec,
            startMs = (startSec * 1000).toLong(),
            endMs = (endSec * 1000).toLong(),
            segmentType = segmentType,
        )

        return SkipIntroApi.submitIntro(apiKey, request)
    }

    suspend fun verifyIntroDbApiKey(apiKey: String): Boolean {
        return SkipIntroApi.verifyIntroDbApiKey(apiKey)
    }

    fun clearCache() {
        cache.clear()
        imdbEntriesCache.clear()
        animeSkipShowIdCache.clear()
        anizipCache.clear()
    }
}
