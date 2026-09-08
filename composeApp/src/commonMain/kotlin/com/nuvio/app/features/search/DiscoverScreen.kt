package com.nuvio.app.features.search

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.network.NetworkStatusRepository
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.nuvioConsumePointerEvents
import com.nuvio.app.features.anilist.AniListDiscoverRepository
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.homeSectionHorizontalPaddingForWidth
import com.nuvio.app.features.home.components.posterGridColumnCountForWidth
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.compose_search_discover_title
import org.jetbrains.compose.resources.stringResource

/**
 * Discover, lifted out of the search screen into its own tab.
 *
 * It was only ever reachable by opening Search and scrolling past the recent-searches list, which
 * buried a browsing surface behind a typing one. The section itself is unchanged — this owns the
 * same [SearchRepository] discover state, refresh and pagination that `SearchScreen` used to.
 */
@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    scrollToTopRequests: Flow<Unit> = emptyFlow(),
    onPosterClick: ((MetaPreview) -> Unit)? = null,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
) {
    LaunchedEffect(Unit) { WatchedRepository.ensureLoaded() }
    val scope = rememberCoroutineScope()

    val discoverUiState by AniListDiscoverRepository.uiState.collectAsStateWithLifecycle()
    val watchedUiState by WatchedRepository.uiState.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()
    val networkStatusUiState by NetworkStatusRepository.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTopRequests) {
        scrollToTopRequests.collect { listState.animateScrollToItem(0) }
    }

    // A new page is a new screenful, so start it at the top rather than mid-grid.
    LaunchedEffect(discoverUiState.currentPage) {
        listState.animateScrollToItem(0)
    }

    // Each filter selection clears the page list, so this reloads whenever one changes.
    LaunchedEffect(
        discoverUiState.selectedType,
        discoverUiState.selectedCatalogKey,
        discoverUiState.selectedGenre,
    ) {
        if (discoverUiState.items.isEmpty()) AniListDiscoverRepository.refresh()
    }


    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val discoverColumns = remember(maxWidth) { posterGridColumnCountForWidth(maxWidth) }

        NuvioScreen(
            horizontalPadding = 0.dp,
            listState = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            stickyHeader {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.background)
                            .nuvioConsumePointerEvents(),
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        NuvioScreenHeader(
                            title = stringResource(Res.string.compose_search_discover_title),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }

            discoverContent(
                state = discoverUiState,
                columns = discoverColumns,
                networkCondition = networkStatusUiState.condition,
                onTypeSelected = AniListDiscoverRepository::selectType,
                onCatalogSelected = AniListDiscoverRepository::selectCatalog,
                onGenreSelected = AniListDiscoverRepository::selectGenre,
                onRetry = {
                    NetworkStatusRepository.requestRefresh(force = true)
                    scope.launch { AniListDiscoverRepository.refresh(forceRefresh = true) }
                },
                watchedKeys = watchedUiState.watchedKeys,
                fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                onPosterClick = onPosterClick,
                onPosterLongClick = onPosterLongClick,
                // The screen header above already says "Discover".
                showHeader = false,
                onPreviousPage = { scope.launch { AniListDiscoverRepository.previousPage() } },
                onNextPage = { scope.launch { AniListDiscoverRepository.nextPage() } },
            )
        }
    }
}
