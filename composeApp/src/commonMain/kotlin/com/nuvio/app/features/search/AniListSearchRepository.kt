package com.nuvio.app.features.search

import co.touchlab.kermit.Logger
import com.nuvio.app.features.anilist.ANILIST_ADDON_ID
import com.nuvio.app.features.anilist.ANILIST_SEARCH_MOVIE_TYPE
import com.nuvio.app.features.anilist.ANILIST_SEARCH_SERIES_TYPE
import com.nuvio.app.features.anilist.AniListCatalogSource
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.HomeCatalogSection
import com.nuvio.app.features.home.HomeCatalogSettingsRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.filterReleasedItems
import com.nuvio.app.features.watchprogress.CurrentDateProvider
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
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_row_movies
import nuvio.composeapp.generated.resources.anilist_source_name
import nuvio.composeapp.generated.resources.compose_search_results_series
import org.jetbrains.compose.resources.getString

/** Results per type, per page. Two types means two rows, so this is the size of each. */
private const val SEARCH_PER_PAGE = 30

/**
 * Text search backed by AniList instead of installed addons.
 *
 * Series and films are fetched concurrently and published as two rows, which keeps the existing
 * row-based search layout intact while making the results anime-first. Both rows carry a paginating
 * [CatalogTarget.AniList] so "see all" continues through AniList rather than dead-ending.
 */
object AniListSearchRepository {
    private val log = Logger.withTag("AniListSearch")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var lastRequestKey: String? = null

    fun search(query: String, forceRefresh: Boolean = false) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            clear()
            return
        }

        val hideUnreleased = HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent
        val includeAdult = AniListCatalogSource.adultContentEnabled()
        val requestKey = "${normalizedQuery.lowercase()}|$hideUnreleased|$includeAdult"
        if (canReuseRequestState(forceRefresh, requestKey, lastRequestKey)) return
        lastRequestKey = requestKey

        activeJob?.cancel()
        _uiState.value = SearchUiState(isLoading = true)

        activeJob = scope.launch {
            try {
                val sections = coroutineScope {
                    val series = async {
                        fetchSection(normalizedQuery, ANILIST_SEARCH_SERIES_TYPE, forceRefresh)
                    }
                    val movies = async {
                        fetchSection(normalizedQuery, ANILIST_SEARCH_MOVIE_TYPE, forceRefresh)
                    }
                    listOfNotNull(series.await(), movies.await())
                }

                _uiState.value = SearchUiState(
                    isLoading = false,
                    sections = sections,
                    emptyStateReason = SearchEmptyStateReason.NoResults.takeIf { sections.isEmpty() },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "AniList search failed for '$normalizedQuery'" }
                lastRequestKey = null
                _uiState.value = SearchUiState(
                    isLoading = false,
                    emptyStateReason = SearchEmptyStateReason.RequestFailed,
                    errorMessage = error.message,
                )
            }
        }
    }

    fun clear() {
        activeJob?.cancel()
        lastRequestKey = null
        _uiState.value = SearchUiState()
    }

    private suspend fun fetchSection(
        query: String,
        contentType: String,
        forceRefresh: Boolean,
    ): HomeCatalogSection? {
        val page = AniListCatalogSource.resolve(
            catalogId = contentType,
            page = 1,
            maxItems = SEARCH_PER_PAGE,
            forceRefresh = forceRefresh,
            searchQuery = query,
        ).withUnreleasedFilter()

        if (page.items.isEmpty()) return null

        return HomeCatalogSection(
            key = "$ANILIST_ADDON_ID:search:$contentType:${query.lowercase()}",
            title = runBlocking { getString(contentType.searchSectionTitle()) },
            subtitle = runBlocking { getString(Res.string.anilist_source_name) },
            addonName = runBlocking { getString(Res.string.anilist_source_name) },
            target = CatalogTarget.AniList(
                catalogId = contentType,
                contentType = contentType,
                searchQuery = query,
            ),
            items = page.items,
            availableItemCount = page.items.size,
            hasMore = page.nextSkip != null,
        )
    }

    private fun String.searchSectionTitle() =
        if (equals(ANILIST_SEARCH_MOVIE_TYPE, ignoreCase = true)) {
            Res.string.anilist_row_movies
        } else {
            Res.string.compose_search_results_series
        }

    private fun CatalogPage.withUnreleasedFilter(): CatalogPage {
        if (!HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent) return this
        val filtered: List<MetaPreview> = items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
        return if (filtered.size == items.size) this else copy(items = filtered)
    }
}
