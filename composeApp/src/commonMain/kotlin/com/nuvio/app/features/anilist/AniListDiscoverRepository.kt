package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.ANILIST_MEDIA_FIELDS
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListPage
import com.nuvio.app.core.anilist.toMetaPreview
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.anime.PublicAnimeRouter
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.search.DiscoverCatalogOption
import com.nuvio.app.features.search.DiscoverEmptyStateReason
import com.nuvio.app.features.search.DiscoverUiState
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * One screenful: 24 divides evenly into both the 3-column phone grid (8 rows) and the 4-column one
 * (6 rows). Discover pages explicitly rather than scrolling forever, so one page is one request and
 * AniList's 30-per-minute limit is unreachable by browsing.
 */
private const val PER_PAGE = 24

/** Content types the filter row offers, using the app's own type names. */
private const val TYPE_SERIES = "series"
private const val TYPE_MOVIE = "movie"

/**
 * The sort options Discover exposes. These fill the "catalog" slot of [DiscoverUiState], which the
 * addon-backed Discover used for an addon's catalogs — the shape fits without changing the UI.
 */
private data class AniListDiscoverSort(
    val key: String,
    val label: String,
    /** Extra arguments spliced into the `media(...)` selector. */
    val selector: String,
)

private val ANILIST_DISCOVER_SORTS = listOf(
    // Airing-only for the same reason as the home Trending row: unfiltered TRENDING_DESC ranks on
    // recent activity and surfaces long-finished titles.
    AniListDiscoverSort("trending", "Trending", "sort: TRENDING_DESC, status_in: [RELEASING]"),
    AniListDiscoverSort("popular", "Popular", "sort: POPULARITY_DESC"),
    AniListDiscoverSort("top-rated", "Top Rated", "sort: SCORE_DESC, averageScore_greater: 60"),
    AniListDiscoverSort("newest", "Newest", "sort: START_DATE_DESC, status_in: [RELEASING, FINISHED]"),
    AniListDiscoverSort("upcoming", "Upcoming", "sort: POPULARITY_DESC, status: NOT_YET_RELEASED"),
)

/**
 * Drives the Discover tab from AniList instead of installed addons.
 *
 * It publishes the same [DiscoverUiState] the addon-backed Discover did, so `discoverContent` and its
 * type/sort/genre filter row work unchanged. Genres come from AniList's own `GenreCollection`, so the
 * list stays correct without being hard-coded here.
 */
object AniListDiscoverRepository {
    private val log = Logger.withTag("AniListDiscover")
    private val loadMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val hydrated = atomic(false)
    private var filtersJob: Job? = null

    private val _uiState = MutableStateFlow(
        DiscoverUiState(
            typeOptions = listOf(TYPE_SERIES, TYPE_MOVIE),
            selectedType = TYPE_SERIES,
            catalogOptions = catalogOptions(TYPE_SERIES, emptyList()),
            selectedCatalogKey = ANILIST_DISCOVER_SORTS.first().key,
        ),
    )
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    /**
     * Pages already fetched for a given filter combination, so paging back — or returning to a
     * filter you had open earlier — costs nothing. [AniListClient] also caches the raw responses,
     * but this keeps the mapped previews too.
     */
    private val pageCache = mutableMapOf<String, CachedPage>()

    private var genres: List<String> = emptyList()
    /** Names from [genres] that are AniList *tags*, which take a different query argument. */
    private var tagNames: Set<String> = emptySet()

    fun selectType(type: String) {
        if (_uiState.value.selectedType == type) return
        _uiState.value = _uiState.value.copy(
            selectedType = type,
            catalogOptions = catalogOptions(type, genres),
            items = emptyList(),
            nextSkip = null,
            emptyStateReason = null,
            errorMessage = null,
        )
    }

    fun selectCatalog(key: String) {
        if (_uiState.value.selectedCatalogKey == key) return
        _uiState.value = _uiState.value.copy(
            selectedCatalogKey = key,
            items = emptyList(),
            currentPage = 1,
            hasPreviousPage = false,
            hasNextPage = false,
            nextSkip = null,
            emptyStateReason = null,
            errorMessage = null,
        )
    }

    fun selectGenre(genre: String?) {
        if (_uiState.value.selectedGenre == genre) return
        _uiState.value = _uiState.value.copy(
            selectedGenre = genre,
            items = emptyList(),
            currentPage = 1,
            hasPreviousPage = false,
            hasNextPage = false,
            nextSkip = null,
            emptyStateReason = null,
            errorMessage = null,
        )
    }

    /** Loads the first page for the current filters. */
    suspend fun refresh(forceRefresh: Boolean = false) = load(page = 1, forceRefresh = forceRefresh)

    suspend fun nextPage() {
        val state = _uiState.value
        if (!state.hasNextPage || state.isLoading) return
        load(page = state.currentPage + 1, forceRefresh = false)
    }

    suspend fun previousPage() {
        val state = _uiState.value
        if (!state.hasPreviousPage || state.isLoading) return
        load(page = state.currentPage - 1, forceRefresh = false)
    }

    private suspend fun load(page: Int, forceRefresh: Boolean) = loadMutex.withLock {
        val current = _uiState.value
        if (current.isLoading) return@withLock
        val sort = ANILIST_DISCOVER_SORTS.firstOrNull { it.key == current.selectedCatalogKey }
            ?: ANILIST_DISCOVER_SORTS.first()

        // Pages and filter chips from the last session, so opening the tab paints without a request.
        hydrateFromCache()
        // Genres and tags feed the chip row, not the grid, so they resolve alongside rather than in
        // front of it: those two extra requests were most of what made Discover slow to open.
        ensureFiltersLoaded()

        val cacheKey = cacheKey(current.selectedType, sort.key, current.selectedGenre, page)
        pageCache[cacheKey]?.takeIf { !forceRefresh }?.let { cached ->
            _uiState.value = current.copy(
                items = cached.items,
                isLoading = false,
                currentPage = page,
                hasPreviousPage = page > 1,
                hasNextPage = cached.hasNextPage,
                nextSkip = null,
                emptyStateReason = DiscoverEmptyStateReason.NoResults.takeIf { cached.items.isEmpty() },
                errorMessage = null,
            )
            return@withLock
        }

        _uiState.value = current.copy(isLoading = true, errorMessage = null, emptyStateReason = null)

        try {
            val fetched = withContext(Dispatchers.Default) {
                fetchPage(
                    page = page,
                    type = current.selectedType ?: TYPE_SERIES,
                    genre = current.selectedGenre,
                    sort = sort,
                    forceRefresh = forceRefresh,
                )
            }
            val items = fetched.items.distinctBy { item -> "${item.type}:${item.id}" }
            pageCache[cacheKey] = CachedPage(items = items, hasNextPage = fetched.nextPage != null)
            _uiState.value = _uiState.value.copy(
                catalogOptions = catalogOptions(current.selectedType ?: TYPE_SERIES, genres),
                items = items,
                isLoading = false,
                currentPage = page,
                hasPreviousPage = page > 1,
                hasNextPage = fetched.nextPage != null,
                nextSkip = null,
                emptyStateReason = DiscoverEmptyStateReason.NoResults.takeIf { items.isEmpty() },
                errorMessage = null,
            )
            withContext(Dispatchers.Default) { AniListDiscoverCache.savePages(pageCache) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "AniList discover load failed" }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                emptyStateReason = DiscoverEmptyStateReason.RequestFailed.takeIf { _uiState.value.items.isEmpty() },
                errorMessage = error.message,
            )
        }
    }

    /**
     * Fills [pageCache] and the filter chips from disk, once per process. The in-memory caches die
     * with the process, so without this every launch paid for the first page again.
     */
    private suspend fun hydrateFromCache() {
        if (!hydrated.compareAndSet(expect = false, update = true)) return

        val (filters, pages) = withContext(Dispatchers.Default) {
            AniListDiscoverCache.loadFilters() to AniListDiscoverCache.loadPages()
        }
        if (filters != null && genres.isEmpty()) {
            genres = filters.genres
            tagNames = filters.tagNames
            _uiState.value = _uiState.value.copy(
                catalogOptions = catalogOptions(_uiState.value.selectedType ?: TYPE_SERIES, genres),
            )
        }
        // A page fetched in this session is fresher than the stored one.
        pages?.forEach { (key, cached) -> if (!pageCache.containsKey(key)) pageCache[key] = cached }
    }

    /**
     * Includes the active provider and the adult filter, not just the visible filter row.
     *
     * AniList, MAL and Kitsu all fill these same pages, and the pages are persisted to disk, so a
     * key built only from type/sort/genre/page kept serving whichever provider happened to populate
     * it first — switching the source in settings left Discover showing the previous provider's
     * results indefinitely. The adult flag is here for the same reason.
     */
    private fun cacheKey(type: String?, sort: String, genre: String?, page: Int): String {
        val source = PublicAnimeRouter.activeProvider.name.lowercase()
        val includeAdult = AniListCatalogSource.adultContentEnabled()
        return "${type.orEmpty()}|$sort|${genre.orEmpty()}|$page|$source|adult=$includeAdult"
    }

data class AniListDiscoverPage(val items: List<MetaPreview>, val nextPage: Int?)

    private suspend fun fetchPage(
        page: Int,
        type: String,
        genre: String?,
        sort: AniListDiscoverSort,
        forceRefresh: Boolean,
    ): AniListDiscoverPage {
        val result = com.nuvio.app.features.anime.PublicAnimeSource.discover(
            page = page,
            contentType = type,
            genre = genre,
            sort = sort.key,
            forceRefresh = forceRefresh,
        ) {
            val fetched = fetchAniListPage(page, type, genre, sort, forceRefresh)
            com.nuvio.app.features.catalog.CatalogPage(
                items = fetched.items,
                rawItemCount = fetched.items.size,
                nextSkip = fetched.nextPage,
            )
        }
        return AniListDiscoverPage(result.items, result.nextSkip)
    }

    private suspend fun fetchAniListPage(
        page: Int,
        type: String,
        genre: String?,
        sort: AniListDiscoverSort,
        forceRefresh: Boolean,
    ): AniListDiscoverPage {
        val includeAdult = AniListCatalogSource.adultContentEnabled()
        val formatFilter = if (type == TYPE_MOVIE) ", format_in: [MOVIE]" else ", format_not_in: [MOVIE]"
        // The filter row is single-select. AniList splits its browse vocabulary into `genre`
        // (18 broad buckets) and `tag` (hundreds of finer themes), queried by different arguments.
        val selection = genre?.takeIf { it.isNotBlank() }
        val genreFilter = when {
            selection == null -> ""
            selection in tagNames -> ", tag: \"$selection\""
            else -> ", genre: \"$selection\""
        }

        val query = """
            query {
                Page(page: $page, perPage: $PER_PAGE) {
                    pageInfo { currentPage hasNextPage }
                    media(type: ANIME${aniListAdultFilter(includeAdult)}, ${sort.selector}$formatFilter$genreFilter) {
                        $ANILIST_MEDIA_FIELDS
                    }
                }
            }
        """.trimIndent()

        val data = AniListClient.query(query = query, forceRefresh = forceRefresh)
        val pageObject = data["Page"] as? JsonObject ?: return AniListDiscoverPage(emptyList(), null)
        val parsed = AniListClient.json.decodeFromJsonElement(AniListPage.serializer(), pageObject)
        return AniListDiscoverPage(
            items = parsed.media.mapNotNull { it.toMetaPreview() },
            nextPage = (page + 1).takeIf { parsed.pageInfo?.hasNextPage == true && parsed.media.isNotEmpty() },
        )
    }

    /**
     * Resolves the chip row's vocabulary without holding up the grid. AniList's 18 genres and the
     * curated tags below are two independent queries, so they run concurrently and publish whenever
     * they land; both are pulled from the API so a name can never drift from what it actually
     * accepts, and a bad entry here drops out silently instead of producing an empty grid.
     */
    private fun ensureFiltersLoaded() {
        if (genres.isNotEmpty()) return
        if (filtersJob?.isActive == true) return

        filtersJob = scope.launch {
            val (apiGenres, resolvedTags) = coroutineScope {
                val genresDeferred = async { fetchGenres() }
                val tagsDeferred = async { fetchTags() }
                genresDeferred.await() to tagsDeferred.await()
            }
            if (apiGenres.isEmpty() && resolvedTags.isEmpty()) return@launch

            tagNames = resolvedTags.toSet()
            genres = apiGenres + resolvedTags
            _uiState.value = _uiState.value.copy(
                catalogOptions = catalogOptions(_uiState.value.selectedType ?: TYPE_SERIES, genres),
            )
            AniListDiscoverCache.saveFilters(genres = genres, tagNames = tagNames)
        }
    }

    private suspend fun fetchGenres(): List<String> = runCatching {
        AniListClient.query("query { GenreCollection }")["GenreCollection"]
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.content.takeIf { name -> name.isNotBlank() } }
            .orEmpty()
    }.onFailure { error ->
        log.w(error) { "AniList genre list lookup failed" }
    }.getOrDefault(emptyList())

    private suspend fun fetchTags(): List<String> = runCatching {
        val collection = AniListClient
            .query("query { MediaTagCollection { name isAdult isGeneralSpoiler } }")["MediaTagCollection"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { element ->
                val tag = element as? JsonObject ?: return@mapNotNull null
                val name = tag["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val isAdult = tag["isAdult"]?.jsonPrimitive?.content == "true"
                val isSpoiler = tag["isGeneralSpoiler"]?.jsonPrimitive?.content == "true"
                name.takeIf { !isAdult && !isSpoiler }
            }
            .toSet()
        // Order follows CURATED_TAGS so the chip row reads deliberately rather than alphabetically.
        CURATED_TAGS.filter { it in collection }
    }.onFailure { error ->
        log.w(error) { "AniList tag list lookup failed" }
    }.getOrDefault(emptyList())
}

private fun catalogOptions(type: String, genres: List<String>): List<DiscoverCatalogOption> =
    ANILIST_DISCOVER_SORTS.map { sort ->
        DiscoverCatalogOption(
            key = sort.key,
            addonName = "AniList",
            manifestUrl = "",
            type = type,
            catalogId = sort.key,
            catalogName = sort.label,
            genreOptions = genres,
            genreRequired = false,
            supportsPagination = true,
        )
    }

private data class CachedPage(
    val items: List<MetaPreview>,
    val hasNextPage: Boolean,
)

private data class DiscoverFilters(
    val genres: List<String>,
    val tagNames: Set<String>,
)

/**
 * Discover's two disk caches.
 *
 * The filter vocabulary changes maybe once a season, so it is kept for a month and costs no requests
 * after the first run. Pages are kept for a day and bounded to the most recent handful, which is
 * enough for the tab to open on content and for paging back to be free.
 */
private object AniListDiscoverCache {
    // 2: the Trending sort gained a RELEASING filter, and the page cache key carries only the sort
    // name, so stored pages from the unfiltered query would otherwise keep being served.
    private const val VERSION = 2
    private const val FILTERS_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val PAGES_MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val MAX_PAGES = 8

    private val log = Logger.withTag("AniListDiscoverCache")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun loadFilters(): DiscoverFilters? {
        val stored = decode<StoredDiscoverFilters>(ANILIST_DISCOVER_FILTERS_CACHE_KEY) ?: return null
        if (stored.version != VERSION || stored.isExpired(FILTERS_MAX_AGE_MS)) return null
        if (stored.genres.isEmpty()) return null
        return DiscoverFilters(genres = stored.genres, tagNames = stored.tagNames.toSet())
    }

    fun saveFilters(genres: List<String>, tagNames: Set<String>) {
        if (genres.isEmpty()) return
        encode(
            key = ANILIST_DISCOVER_FILTERS_CACHE_KEY,
            payload = StoredDiscoverFilters(
                version = VERSION,
                storedAtEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
                genres = genres,
                tagNames = tagNames.toList(),
            ),
        )
    }

    fun loadPages(): Map<String, CachedPage>? {
        val stored = decode<StoredDiscoverPages>(ANILIST_DISCOVER_PAGES_CACHE_KEY) ?: return null
        if (stored.version != VERSION || stored.isExpired(PAGES_MAX_AGE_MS)) return null
        return stored.pages
            .filter { page -> page.key.isNotBlank() && page.items.isNotEmpty() }
            .associate { page -> page.key to CachedPage(items = page.items, hasNextPage = page.hasNextPage) }
            .takeIf { it.isNotEmpty() }
    }

    fun savePages(pages: Map<String, CachedPage>) {
        // Newest keys last in a LinkedHashMap, so the tail is what the user was just looking at.
        val recent = pages.entries.toList().takeLast(MAX_PAGES)
        if (recent.isEmpty()) return
        encode(
            key = ANILIST_DISCOVER_PAGES_CACHE_KEY,
            payload = StoredDiscoverPages(
                version = VERSION,
                storedAtEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
                pages = recent.map { (key, cached) ->
                    StoredDiscoverPage(
                        key = key,
                        items = cached.items,
                        hasNextPage = cached.hasNextPage,
                    )
                },
            ),
        )
    }

    private inline fun <reified T> decode(key: String): T? {
        val payload = AniListCacheStorage.loadPayload(key)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString<T>(payload) }
            .onFailure { error -> log.w(error) { "Discarding unreadable discover cache ($key)" } }
            .getOrNull()
    }

    private inline fun <reified T> encode(key: String, payload: T) {
        runCatching { AniListCacheStorage.savePayload(key, json.encodeToString(payload)) }
            .onFailure { error -> log.w(error) { "Failed to persist discover cache ($key)" } }
    }
}

private interface StoredWithTimestamp {
    val storedAtEpochMs: Long
}

private fun StoredWithTimestamp.isExpired(maxAgeMs: Long): Boolean =
    storedAtEpochMs <= 0L || EpisodeReleaseDatePlatform.nowEpochMs() - storedAtEpochMs > maxAgeMs

@Serializable
private data class StoredDiscoverFilters(
    val version: Int = 0,
    override val storedAtEpochMs: Long = 0L,
    val genres: List<String> = emptyList(),
    val tagNames: List<String> = emptyList(),
) : StoredWithTimestamp

@Serializable
private data class StoredDiscoverPages(
    val version: Int = 0,
    override val storedAtEpochMs: Long = 0L,
    val pages: List<StoredDiscoverPage> = emptyList(),
) : StoredWithTimestamp

@Serializable
private data class StoredDiscoverPage(
    val key: String = "",
    val items: List<MetaPreview> = emptyList(),
    val hasNextPage: Boolean = false,
)

/**
 * Finer-grained browse options on top of AniList's 18 genres, drawn from its tag vocabulary. Kept to
 * the ones people actually browse by — the full collection is 353 usable tags, far too many for a
 * chip row. Each is validated against the API before being offered.
 */
private val CURATED_TAGS = listOf(
    "Isekai",
    "Shounen",
    "Seinen",
    "Shoujo",
    "Josei",
    "School",
    "Magic",
    "Martial Arts",
    "Military",
    "Post-Apocalyptic",
    "Cyberpunk",
    "Space",
    "Robots",
    "Iyashikei",
    "Vampire",
    "Samurai",
    "Ninja",
    "Assassins",
    "Food",
    "Idol",
    "Revenge",
    "Survival",
    "Super Power",
    "Coming of Age",
)
