package com.nuvio.app.features.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.features.anilist.AniListListRepository
import com.nuvio.app.features.anilist.AniListListTab
import com.nuvio.app.features.anilist.aniListEntriesForTab
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.components.PosterGridRow
import com.nuvio.app.features.home.components.posterGridColumnCountForWidth
import com.nuvio.app.features.watched.WatchedRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.anilist_lists_empty
import nuvio.composeapp.generated.resources.anilist_lists_status_completed
import nuvio.composeapp.generated.resources.anilist_lists_status_dropped
import nuvio.composeapp.generated.resources.anilist_lists_status_paused
import nuvio.composeapp.generated.resources.anilist_lists_status_planning
import nuvio.composeapp.generated.resources.anilist_lists_status_watching
import nuvio.composeapp.generated.resources.anilist_lists_title
import org.jetbrains.compose.resources.stringResource

@Composable
private fun AniListListTab.label(): String = stringResource(
    when (this) {
        AniListListTab.WATCHING -> Res.string.anilist_lists_status_watching
        AniListListTab.COMPLETED -> Res.string.anilist_lists_status_completed
        AniListListTab.PAUSED -> Res.string.anilist_lists_status_paused
        AniListListTab.DROPPED -> Res.string.anilist_lists_status_dropped
        AniListListTab.PLANNING -> Res.string.anilist_lists_status_planning
    },
)

/**
 * Full-screen AniList lists browser. Status tabs across the top, a scrollable multi-column poster
 * grid below. All data is the already-cached lists snapshot — [aniListEntriesForTab] is a pure
 * projection, so switching tabs does no network work.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AniListListsScreen(
    onBack: () -> Unit,
    onPosterClick: (MetaPreview) -> Unit,
    onPosterLongClick: ((MetaPreview) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listsState by AniListListRepository.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { AniListListRepository.ensureLoaded() }
    val watchedKeys by remember { WatchedRepository.uiState }.collectAsStateWithLifecycle()
    val fullyWatchedSeriesKeys by WatchedRepository.fullyWatchedSeriesKeys.collectAsStateWithLifecycle()

    var selectedTabName by rememberSaveable { mutableStateOf(AniListListTab.WATCHING.name) }
    val selectedTab = remember(selectedTabName) {
        runCatching { AniListListTab.valueOf(selectedTabName) }.getOrDefault(AniListListTab.WATCHING)
    }

    val entries = remember(listsState.snapshot, selectedTab) {
        aniListEntriesForTab(listsState.snapshot, selectedTab)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = remember(maxWidth) { posterGridColumnCountForWidth(maxWidth) }

        NuvioScreen(
            modifier = Modifier.fillMaxSize(),
            horizontalPadding = 0.dp,
        ) {
            stickyHeader {
                NuvioScreenHeader(
                    title = stringResource(Res.string.anilist_lists_title),
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item(key = "anilist-lists-tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AniListListTab.entries.forEach { tab ->
                        val count = remember(listsState.snapshot, tab) {
                            aniListEntriesForTab(listsState.snapshot, tab).size
                        }
                        FilterChip(
                            selected = tab == selectedTab,
                            onClick = { selectedTabName = tab.name },
                            label = { Text("${tab.label()} · $count") },
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                item(key = "anilist-lists-empty") {
                    Text(
                        text = stringResource(Res.string.anilist_lists_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    )
                }
            } else {
                items(
                    items = entries.chunked(columns),
                    key = { row -> "anilist-lists-row:${selectedTab.name}:${row.first().id}" },
                ) { rowItems ->
                    PosterGridRow(
                        items = rowItems.map(LibraryItem::toMetaPreview),
                        columns = columns,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        watchedKeys = watchedKeys.watchedKeys,
                        fullyWatchedSeriesKeys = fullyWatchedSeriesKeys,
                        onPosterClick = { preview -> onPosterClick(preview) },
                        onPosterLongClick = onPosterLongClick,
                    )
                }
            }
        }
    }
}
