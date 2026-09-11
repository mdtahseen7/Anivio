package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.anime.PublicAnimeSource
import com.nuvio.app.features.anilist.AniListHeroArtwork
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.collection.Collection
import com.nuvio.app.features.collection.CollectionRepository
import com.nuvio.app.features.collection.CollectionSource
import com.nuvio.app.features.collection.TmdbCollectionSourceResolver
import com.nuvio.app.features.collection.catalogRouteKey
import com.nuvio.app.features.collection.findCollectionCatalog
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.random.Random

object HomeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Bumped every time home state is discarded, so the screen can re-request its rows.
     *
     * [clear] wipes the definitions the rows are built from, but the effect that reloads them is
     * keyed on the addon set and the active profile — neither of which changes when the app is
     * resumed and `AppGate` re-selects the *same* profile. That left an empty home until the tab was
     * disposed and recomposed. Observing this counter covers every clear path, including future ones,
     * rather than adding another key each time one is discovered.
     */
    private val _reloadGeneration = MutableStateFlow(0)
    val reloadGeneration: StateFlow<Int> = _reloadGeneration.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequestKey: String? = null
    private var currentRequestKey: String? = null
    private var currentDefinitions: List<HomeCatalogDefinition> = emptyList()
    private var cachedSections: Map<String, HomeCatalogSection> = emptyMap()
    private var cachedCollectionHeroItems: List<MetaPreview> = emptyList()
    private var collectionHeroJob: Job? = null
    private var heroArtworkJob: Job? = null
    private var collectionHeroRequestKey: String? = null
    private var lastPublishedCatalogHeroEmpty: Boolean = true
    private var lastErrorMessage: String? = null
    private var hydratedFromCache: Boolean = false

    fun refresh(addons: List<ManagedAddon>, force: Boolean = false) {
        val activeAddons = addons.enabledAddons()
        val requests = buildAllHomeCatalogDefinitions(activeAddons)
        currentDefinitions = requests
        val requestCacheKeys = requests.mapTo(mutableSetOf(), HomeCatalogDefinition::cacheKey)
        cachedSections = cachedSections.filterKeys(requestCacheKeys::contains)
        val requestKey = requests.joinToString(separator = "|", transform = HomeCatalogDefinition::cacheKey)
        currentRequestKey = requestKey

        if (!force && activeRequestKey == requestKey && _uiState.value.isLoading) return
        activeRequestKey = requestKey

        if (requests.isEmpty()) {
            activeJob?.cancel()
            activeJob = null
            activeRequestKey = null
            cachedSections = emptyMap()
            lastErrorMessage = null
            publishCurrentState(
                isLoading = false,
                requestKey = requestKey,
            )
            ensureCollectionHeroFallback(
                addons = activeAddons,
                forceRefresh = force,
                refreshSources = true,
                requestKey = requestKey,
            )
            return
        }

        activeJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        activeJob = scope.launch {
            // Paint last session's rows first: the network refresh below then happens behind
            // content the user can already read instead of behind skeletons.
            hydrateFromCache(requestKey = requestKey)
            val prioritizedRequests = prioritizeDefinitions(
                definitions = requests,
                snapshot = HomeCatalogSettingsRepository.snapshot(),
            )
            // One aliased GraphQL call for every AniList row, so a refresh costs one request
            // against AniList's 30/minute budget instead of one per row.
            val aniListPages = fetchAniListHomePages(
                definitions = prioritizedRequests,
                forceRefresh = force,
            )
            val loadedSections = linkedMapOf<String, HomeCatalogSection>().apply {
                putAll(cachedSections)
            }
            var firstErrorMessage: String? = null
            var batchIndex = 0

            prioritizedRequests.chunked(HOME_CATALOG_FETCH_BATCH_SIZE).forEach { batch ->
                if (activeRequestKey != requestKey) return@launch
                val results = batch.map { request ->
                    async {
                        request to runCatching {
                            request.toSection(
                                forceRefresh = force,
                                aniListPages = aniListPages,
                            )
                        }
                    }
                }.awaitAll()

                if (activeRequestKey != requestKey) return@launch

                results.mapNotNull { (request, result) ->
                    result.getOrNull()?.let { section -> request.cacheKey to section }
                }.forEach { (cacheKey, section) ->
                    loadedSections[cacheKey] = section
                }
                if (firstErrorMessage == null) {
                    firstErrorMessage = results.firstNotNullOfOrNull { (_, result) ->
                        result.exceptionOrNull()?.message
                    }
                }
                cachedSections = loadedSections.toMap()
                lastErrorMessage = firstErrorMessage
                if (batchIndex == 0 || (batchIndex + 1) % HOME_CATALOG_PUBLISH_INTERVAL == 0) {
                    publishCurrentState(
                        isLoading = true,
                        requestKey = requestKey,
                    )
                }
                batchIndex++
            }

            if (activeRequestKey != requestKey) return@launch

            cachedSections = loadedSections.toMap()
            lastErrorMessage = firstErrorMessage
            activeRequestKey = null
            publishCurrentState(
                isLoading = false,
                requestKey = requestKey,
            )
            ensureCollectionHeroFallback(
                addons = activeAddons,
                forceRefresh = force,
                refreshSources = true,
                requestKey = requestKey,
            )
            persistCache()
        }
    }

    fun applyCurrentSettings() {
        publishCurrentState(
            isLoading = _uiState.value.isLoading,
            requestKey = currentRequestKey,
        )
        ensureCollectionHeroFallback(
            addons = AddonRepository.uiState.value.addons.enabledAddons(),
            forceRefresh = false,
            refreshSources = false,
            requestKey = currentRequestKey,
        )
    }

    fun clear() {
        activeJob?.cancel()
        activeJob = null
        activeRequestKey = null
        currentRequestKey = null
        currentDefinitions = emptyList()
        cachedSections = emptyMap()
        cachedCollectionHeroItems = emptyList()
        collectionHeroJob?.cancel()
        collectionHeroJob = null
        collectionHeroRequestKey = null
        heroArtworkJob?.cancel()
        heroArtworkJob = null
        lastPublishedCatalogHeroEmpty = true
        lastErrorMessage = null
        hydratedFromCache = false
        _uiState.value = HomeUiState()
        _reloadGeneration.update { it + 1 }
    }

    /**
     * Fills empty rows from [HomeCatalogCache] once per process, before the first network response.
     * Rows already in memory are left alone, and a cached row whose definition is gone — uninstalled
     * addon, changed manifest — has no matching cache key, so it cannot come back.
     */
    private fun hydrateFromCache(requestKey: String) {
        if (hydratedFromCache) return
        hydratedFromCache = true

        val snapshot = HomeCatalogCache.load() ?: return
        AniListHeroArtwork.hydrate(snapshot.heroArtwork)

        val restored = currentDefinitions.mapNotNull { definition ->
            if (cachedSections.containsKey(definition.cacheKey)) return@mapNotNull null
            val row = snapshot.rows[definition.cacheKey]?.takeIf { it.items.isNotEmpty() }
                ?: return@mapNotNull null
            definition.cacheKey to definition.toSection(row)
        }
        if (restored.isEmpty() && snapshot.heroArtwork.isEmpty()) return

        cachedSections = cachedSections + restored
        if (activeRequestKey != requestKey) return
        publishCurrentState(
            isLoading = true,
            requestKey = requestKey,
        )
    }

    /** Writes the rows currently on screen, plus any hero artwork resolved for them, to disk. */
    private fun persistCache() {
        HomeCatalogCache.save(
            sections = cachedSections,
            heroArtwork = AniListHeroArtwork.snapshot(),
            // Recorded per row rather than inferred at read time: the cache holds only keys and items,
            // and a key cannot say whether the row it belongs to is about recency.
            volatileCacheKeys = currentDefinitions
                .filter(HomeCatalogDefinition::isRecencyBased)
                .mapTo(mutableSetOf(), HomeCatalogDefinition::cacheKey),
        )
    }

    private fun publishCurrentState(
        isLoading: Boolean,
        requestKey: String?,
    ) {
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        val preferences = snapshot.preferences
        val todayIsoDate = if (snapshot.hideUnreleasedContent) CurrentDateProvider.todayIsoDate() else null
        fun HomeCatalogSection.withReleaseFilter(): HomeCatalogSection =
            if (todayIsoDate == null) this else filterReleasedItems(todayIsoDate)

        val sections = currentDefinitions
            .sortedBy { definition -> preferences[definition.key]?.order ?: Int.MAX_VALUE }
            .mapNotNull { definition ->
                val preference = preferences[definition.key]
                if (preference?.enabled == false) return@mapNotNull null

                val section = cachedSections[definition.cacheKey]?.withReleaseFilter() ?: return@mapNotNull null
                if (section.items.isEmpty()) return@mapNotNull null
                val customTitle = preference?.customTitle.orEmpty()
                section.copy(
                    title = customTitle.ifBlank { definition.titleFor(snapshot.showCatalogType) },
                )
            }

        val catalogHeroItems = if (snapshot.heroEnabled) {
            val heroRandom = Random((requestKey?.hashCode() ?: 0).absoluteValue + 1)
            currentDefinitions
                .filter { definition -> preferences[definition.key]?.heroSourceEnabled != false }
                .mapNotNull { definition -> cachedSections[definition.cacheKey] }
                .map { section -> section.withReleaseFilter() }
                .flatMap { section -> section.items }
                .distinctBy { item -> "${item.type}:${item.id}" }
                .shuffled(heroRandom)
                .take(HOME_HERO_ITEM_LIMIT)
        } else {
            emptyList()
        }
        lastPublishedCatalogHeroEmpty = snapshot.heroEnabled && catalogHeroItems.isEmpty()
        val heroItems = if (snapshot.heroEnabled) {
            catalogHeroItems.ifEmpty { cachedCollectionHeroItems }
        } else {
            emptyList()
        }
        // AniList only has portrait covers and a patchy banner, so swap in the wide backdrop and
        // title logo resolved from TMDB / fanart.tv / ani.zip once they land.
        val heroArtwork = AniListHeroArtwork.snapshot()
        val decoratedHeroItems = heroItems.map { item -> heroArtwork[item.stableKey()] ?: item }

        _uiState.value = HomeUiState(
            isLoading = isLoading,
            heroItems = decoratedHeroItems,
            sections = sections,
            errorMessage = if (sections.isEmpty()) lastErrorMessage else null,
        )

        ensureHeroArtwork(items = decoratedHeroItems, requestKey = requestKey)
    }

    private fun ensureHeroArtwork(items: List<MetaPreview>, requestKey: String?) {
        if (items.isEmpty()) return
        // Never cancel a running pass: publishes fire every couple of catalog batches, and
        // restarting would keep pushing resolution out until loading settled.
        if (heroArtworkJob?.isActive == true) return
        if (!AniListHeroArtwork.hasUnresolved(items)) return

        heroArtworkJob = scope.launch {
            AniListHeroArtwork.resolve(items)
            // Everything passed in is cached now, so this republish cannot re-trigger itself.
            publishCurrentState(
                isLoading = _uiState.value.isLoading,
                requestKey = requestKey,
            )
            // Artwork costs several requests per title, so it is worth keeping past this launch.
            persistCache()
        }
    }

    private suspend fun HomeCatalogDefinition.toSection(
        forceRefresh: Boolean,
        aniListPages: Map<String, CatalogPage>,
    ): HomeCatalogSection {
        val page = when (source) {
            // Normally already resolved by the batched query; falls back to a single-row
            // request if that call failed.
            HomeCatalogSource.ANILIST -> aniListPages[catalogId]
                ?: PublicAnimeSource.catalog(
                    catalogId = catalogId,
                    contentType = type,
                    page = 1,
                    maxItems = HOME_CATALOG_PREVIEW_FETCH_LIMIT,
                    forceRefresh = forceRefresh,
                )

            HomeCatalogSource.ADDON -> fetchCatalogPage(
                manifestUrl = manifestUrl,
                type = type,
                catalogId = catalogId,
                maxItems = HOME_CATALOG_PREVIEW_FETCH_LIMIT,
                forceRefresh = forceRefresh,
            )
        }
        val items = page.items

        return HomeCatalogSection(
            key = key,
            title = defaultTitle,
            subtitle = addonName,
            addonName = addonName,
            target = catalogTarget(),
            items = items,
            availableItemCount = if (items.isEmpty()) 0 else page.rawItemCount,
            hasMore = items.isNotEmpty() && supportsPagination && page.nextSkip != null,
        )
    }

    /** Rebuilds a row from cached items, taking everything but the items from the live definition. */
    private fun HomeCatalogDefinition.toSection(row: HomeCatalogCachedRow): HomeCatalogSection =
        HomeCatalogSection(
            key = key,
            title = defaultTitle,
            subtitle = addonName,
            addonName = addonName,
            target = catalogTarget(),
            items = row.items,
            availableItemCount = row.availableItemCount.takeIf { it > 0 } ?: row.items.size,
            hasMore = row.hasMore && supportsPagination,
        )

    private fun HomeCatalogDefinition.catalogTarget(): CatalogTarget =
        when (source) {
            HomeCatalogSource.ANILIST -> CatalogTarget.AniList(
                catalogId = catalogId,
                contentType = type,
                supportsPagination = supportsPagination,
            )

            HomeCatalogSource.ADDON -> CatalogTarget.Addon(
                manifestUrl = manifestUrl,
                contentType = type,
                catalogId = catalogId,
                supportsPagination = supportsPagination,
            )
        }

    private suspend fun fetchAniListHomePages(
        definitions: List<HomeCatalogDefinition>,
        forceRefresh: Boolean,
    ): Map<String, CatalogPage> {
        val catalogIds = definitions
            .filter { it.source == HomeCatalogSource.ANILIST }
            .map(HomeCatalogDefinition::catalogId)
        if (catalogIds.isEmpty()) return emptyMap()

        return try {
            PublicAnimeSource.homeRows(
                catalogIds = catalogIds,
                maxItems = HOME_CATALOG_PREVIEW_FETCH_LIMIT,
                forceRefresh = forceRefresh,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Not fatal — toSection() retries each row individually and surfaces the error there.
            emptyMap()
        }
    }

    private fun ensureCollectionHeroFallback(
        addons: List<ManagedAddon>,
        forceRefresh: Boolean,
        refreshSources: Boolean,
        requestKey: String?,
    ) {
        if (!lastPublishedCatalogHeroEmpty) return
        val snapshot = HomeCatalogSettingsRepository.snapshot()
        if (!snapshot.heroEnabled) return
        val collections = enabledCollectionsForHero(snapshot)
        if (collections.isEmpty()) {
            cachedCollectionHeroItems = emptyList()
            collectionHeroRequestKey = null
            return
        }

        val nextRequestKey = collectionHeroRequestKey(
            collections = collections,
            addons = addons,
            snapshot = snapshot,
            requestKey = requestKey,
        )
        if (!refreshSources && collectionHeroRequestKey == nextRequestKey) return

        collectionHeroJob?.cancel()
        collectionHeroRequestKey = nextRequestKey
        cachedCollectionHeroItems = emptyList()
        publishCurrentState(
            isLoading = _uiState.value.isLoading,
            requestKey = requestKey,
        )

        collectionHeroJob = scope.launch {
            val sources = collectionHeroSources(collections)
            val sourceResults = sources.map { source ->
                async {
                    runCatching {
                        source.resolveCollectionHeroItems(
                            addons = addons,
                            forceRefresh = forceRefresh,
                        )
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
            val random = Random((nextRequestKey.hashCode()).absoluteValue + 7)
            cachedCollectionHeroItems = roundRobinCollectionHeroItems(sourceResults)
                .distinctBy { item -> item.stableKey() }
                .shuffled(random)
                .take(HOME_HERO_ITEM_LIMIT)
            publishCurrentState(
                isLoading = _uiState.value.isLoading,
                requestKey = requestKey,
            )
        }
    }

    private fun enabledCollectionsForHero(snapshot: HomeCatalogSettingsSnapshot): List<Collection> {
        val preferences = snapshot.preferences
        return CollectionRepository.collections.value
            .filter { collection ->
                collection.folders.isNotEmpty() &&
                    preferences["collection_${collection.id}"]?.enabled != false
            }
            .sortedBy { collection ->
                preferences["collection_${collection.id}"]?.order ?: Int.MAX_VALUE
            }
    }

    private fun collectionHeroSources(collections: List<Collection>): List<CollectionSource> =
        collections
            .flatMap { collection -> collection.folders }
            .flatMap { folder -> folder.resolvedSources }
            .take(HOME_COLLECTION_HERO_SOURCE_LIMIT)

    private suspend fun CollectionSource.resolveCollectionHeroItems(
        addons: List<ManagedAddon>,
        forceRefresh: Boolean,
    ): List<MetaPreview> {
        val page = when {
            isTmdb -> TmdbCollectionSourceResolver.resolve(source = this, page = 1)
            else -> {
                val catalogSource = addonCatalogSource() ?: return emptyList()
                val resolvedCatalog = addons.findCollectionCatalog(catalogSource) ?: return emptyList()
                fetchCatalogPage(
                    manifestUrl = resolvedCatalog.addon.manifestUrl,
                    type = catalogSource.type,
                    catalogId = catalogSource.catalogId,
                    genre = catalogSource.genre,
                    maxItems = HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT,
                    forceRefresh = forceRefresh,
                )
            }
        }
        val items = page.items
        return if (HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) {
            items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
        } else {
            items
        }
    }

    private fun roundRobinCollectionHeroItems(sourceResults: List<List<MetaPreview>>): List<MetaPreview> {
        val iterators = sourceResults.filter { it.isNotEmpty() }.map { it.iterator() }
        if (iterators.isEmpty()) return emptyList()
        val merged = mutableListOf<MetaPreview>()
        var hasMore = true
        while (hasMore && merged.size < HOME_COLLECTION_HERO_SOURCE_LIMIT * HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT) {
            hasMore = false
            iterators.forEach { iterator ->
                if (iterator.hasNext()) {
                    merged.add(iterator.next())
                    hasMore = true
                }
            }
        }
        return merged
    }

    private fun collectionHeroRequestKey(
        collections: List<Collection>,
        addons: List<ManagedAddon>,
        snapshot: HomeCatalogSettingsSnapshot,
        requestKey: String?,
    ): String = buildString {
        append(requestKey.orEmpty())
        append("|hideUnreleased=")
        append(snapshot.hideUnreleasedContent)
        append("|collections=")
        collections.forEach { collection ->
            val preference = snapshot.preferences["collection_${collection.id}"]
            append(collection.id)
            append(":")
            append(preference?.order ?: Int.MAX_VALUE)
            append(":")
            collection.folders.forEach { folder ->
                append(folder.id)
                append("[")
                folder.resolvedSources.forEach { source ->
                    append(collectionSourceKey(source))
                    append(",")
                }
                append("]")
            }
            append(";")
        }
        append("|addons=")
        addons.forEach { addon ->
            append(addon.manifest?.id.orEmpty())
            append(":")
            append(addon.manifestUrl)
            append(":")
            append(addon.manifest?.catalogs?.size ?: 0)
            append(";")
        }
    }

    private fun collectionSourceKey(source: CollectionSource): String =
        source.catalogRouteKey()
}

private const val HOME_HERO_ITEM_LIMIT = 8
private const val HOME_COLLECTION_HERO_SOURCE_LIMIT = 6
private const val HOME_COLLECTION_HERO_SOURCE_ITEM_LIMIT = 8
private const val HOME_CATALOG_FETCH_BATCH_SIZE = 4
private const val HOME_CATALOG_PREVIEW_FETCH_LIMIT = 18
private const val HOME_CATALOG_PUBLISH_INTERVAL = 2

private fun prioritizeDefinitions(
    definitions: List<HomeCatalogDefinition>,
    snapshot: HomeCatalogSettingsSnapshot,
): List<HomeCatalogDefinition> {
    val orderedDefinitions = definitions.sortedBy { definition ->
        snapshot.preferences[definition.key]?.order ?: Int.MAX_VALUE
    }
    val (priority, remainder) = orderedDefinitions.partition { definition ->
        val preference = snapshot.preferences[definition.key]
        if (preference == null) {
            true
        } else {
            preference.enabled || (snapshot.heroEnabled && preference.heroSourceEnabled)
        }
    }
    return priority + remainder
}
