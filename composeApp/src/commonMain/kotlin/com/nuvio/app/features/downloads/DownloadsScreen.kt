package com.nuvio.app.features.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.i18n.localizedByteUnit
import com.nuvio.app.core.ui.NuvioBottomSheetActionRow
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.plugins.PluginRepository
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenMangaDownload: (MangaDownloadItem) -> Unit,
    initialShowId: String? = null,
    onNavigateToShow: ((showId: String, title: String) -> Unit)? = null,
    onBackFromShow: (() -> Unit)? = null,
) {
    val uiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()

    var selectedShowId by rememberSaveable(initialShowId) { mutableStateOf(initialShowId) }
    var downloadPendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    var mangaPendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMediaTab by rememberSaveable { mutableStateOf(0) }
    var showServerPicker by rememberSaveable { mutableStateOf(false) }
    val openDownloadsDirectoryFailedText = stringResource(Res.string.downloads_open_directory_failed)

    val downloadSettings by remember {
        DownloadSettingsRepository.ensureLoaded()
        // The server picker lists installed plugins; make sure they are loaded even if the plugins
        // screen has not been opened this session.
        PluginRepository.initialize()
        DownloadSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    val mangaUiState by remember {
        MangaDownloadsRepository.ensureLoaded()
        MangaDownloadsRepository.uiState
    }.collectAsStateWithLifecycle()

    val completedEpisodes = remember(uiState.items) {
        uiState.completedItems
            .filter { it.isEpisode }
            .sortedForSeriesDownloads()
    }

    val selectedShowTitle = remember(selectedShowId, completedEpisodes) {
        selectedShowId?.let { showId ->
            completedEpisodes.firstOrNull { it.parentMetaId == showId }?.title
        }
    }

    NuvioScreen(horizontalPadding = 20.dp, topPadding = 0.dp) {
        stickyHeader {
            DownloadsHeader(
                title = if (selectedShowId == null) {
                    stringResource(Res.string.compose_settings_root_downloads_title)
                } else {
                    selectedShowTitle ?: stringResource(Res.string.downloads_show_downloads)
                },
                onBack = {
                    if (selectedShowId != null) {
                        onBackFromShow?.invoke() ?: run { selectedShowId = null }
                    } else {
                        onBack()
                    }
                },
                onOpenFolder = {
                    if (!DownloadsPlatformDownloader.openDownloadsDirectory()) {
                        NuvioToastController.show(openDownloadsDirectoryFailedText)
                    }
                },
            )
        }

        if (selectedShowId == null) {
            item {
                DownloadsMediaTabs(
                    selectedIndex = selectedMediaTab,
                    onSelected = { selectedMediaTab = it },
                )
            }
            if (selectedMediaTab == 0) {
                downloadsRootContent(
                    uiState = uiState,
                    mangaUiState = mangaUiState,
                    defaultServerName = downloadSettings.defaultServerAddonName,
                    onPickDefaultServer = { showServerPicker = true },
                    onOpenDownload = onOpenDownload,
                    onOpenShow = { showId, title ->
                        onNavigateToShow?.invoke(showId, title) ?: run { selectedShowId = showId }
                    },
                    onDeleteDownload = { downloadPendingDeletionId = it },
                )
            } else {
                downloadsMangaContent(
                    uiState = mangaUiState,
                    animeUiState = uiState,
                    onOpenDownload = onOpenMangaDownload,
                    onDeleteDownload = { mangaPendingDeletionId = it },
                )
            }
        } else {
            downloadsShowContent(
                showId = selectedShowId.orEmpty(),
                episodes = completedEpisodes,
                onOpenDownload = onOpenDownload,
                onDeleteDownload = { downloadPendingDeletionId = it },
            )
        }
    }

    val pendingDeletionId = downloadPendingDeletionId
    if (pendingDeletionId != null) {
        NuvioStatusModal(
            title = stringResource(Res.string.action_delete_confirm_title),
            message = stringResource(Res.string.action_delete_confirm_message),
            isVisible = true,
            confirmText = stringResource(Res.string.action_yes),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                DownloadsRepository.cancelDownload(pendingDeletionId)
                downloadPendingDeletionId = null
            },
            onDismiss = { downloadPendingDeletionId = null },
        )
    }

    val pendingMangaDeletionId = mangaPendingDeletionId
    if (pendingMangaDeletionId != null) {
        NuvioStatusModal(
            title = stringResource(Res.string.action_delete_confirm_title),
            message = stringResource(Res.string.action_delete_confirm_message),
            isVisible = true,
            confirmText = stringResource(Res.string.action_yes),
            dismissText = stringResource(Res.string.action_no),
            onConfirm = {
                MangaDownloadsRepository.cancelDownload(pendingMangaDeletionId)
                mangaPendingDeletionId = null
            },
            onDismiss = { mangaPendingDeletionId = null },
        )
    }

    if (showServerPicker) {
        DefaultDownloadServerSheet(
            currentServerName = downloadSettings.defaultServerAddonName,
            onSelect = {
                DownloadSettingsRepository.setDefaultServerAddonName(it)
            },
            onDismiss = { showServerPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultDownloadServerSheet(
    currentServerName: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val tokens = MaterialTheme.nuvio
    // The default download server is a locally-installed plugin (scraper): downloads are fetched
    // from this plugin only. List enabled scrapers by name (the name is what stream matching keys on).
    val pluginsState by PluginRepository.uiState.collectAsStateWithLifecycle()
    val serverNames = remember(pluginsState) {
        pluginsState.scrapers.filter { it.enabled }.map { it.name }.distinct()
    }

    fun choose(name: String?) {
        onSelect(name)
        scope.launch { dismissNuvioBottomSheet(sheetState, onDismiss) }
    }

    NuvioModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Text(
                text = stringResource(Res.string.download_default_server),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            NuvioBottomSheetActionRow(
                title = stringResource(Res.string.download_default_server_none),
                onClick = { choose(null) },
                trailingContent = if (currentServerName.isNullOrBlank()) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.accent) }
                } else null,
            )
            serverNames.forEach { name ->
                NuvioBottomSheetActionRow(
                    title = name,
                    onClick = { choose(name) },
                    trailingContent = if (currentServerName == name) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.accent) }
                    } else null,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun LazyListScope.downloadsRootContent(
    uiState: DownloadsUiState,
    mangaUiState: MangaDownloadsUiState,
    defaultServerName: String?,
    onPickDefaultServer: () -> Unit,
    onOpenDownload: (DownloadItem) -> Unit,
    onOpenShow: (showId: String, title: String) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    item {
        DownloadsManagementBar(
            summary = mergeStorageSummaries(uiState.storageSummary(), mangaUiState.storageSummary()),
            onPauseAll = {
                DownloadsRepository.pauseActiveDownloads()
                MangaDownloadsRepository.pauseActiveDownloads()
            },
            onResumeAll = {
                DownloadsRepository.resumePausedDownloads()
                MangaDownloadsRepository.resumePausedDownloads()
            },
            onRetryAll = {
                DownloadsRepository.retryFailedDownloads()
                MangaDownloadsRepository.retryFailedDownloads()
            },
        )
    }
    item {
        DefaultServerRow(
            serverName = defaultServerName,
            onClick = onPickDefaultServer,
        )
    }

    val activeItems = uiState.activeItems
    val completedMovies = uiState.completedItems.filterNot(DownloadItem::isEpisode)
    val completedShows = uiState.completedItems
        .filter(DownloadItem::isEpisode)
        .groupBy { it.parentMetaId }
        .mapNotNull { (_, episodes) ->
            episodes.firstOrNull()?.let { first ->
                first to episodes
            }
        }
        .sortedBy { (item, _) -> item.title.lowercase() }

    if (activeItems.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_active), activeItems.size)
        }
        items(
            items = activeItems,
            key = { it.id },
        ) { item ->
            DownloadRow(
                item = item,
                onOpen = { onOpenDownload(item) },
                onPause = { DownloadsRepository.pauseDownload(item.id) },
                onResume = { DownloadsRepository.resumeDownload(item.id) },
                onRetry = { DownloadsRepository.retryDownload(item.id) },
                onDelete = { onDeleteDownload(item.id) },
            )
        }
    }

    if (completedMovies.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_movies), completedMovies.size)
        }
        items(
            items = completedMovies,
            key = { it.id },
        ) { item ->
            DownloadRow(
                item = item,
                onOpen = { onOpenDownload(item) },
                onPause = { DownloadsRepository.pauseDownload(item.id) },
                onResume = { DownloadsRepository.resumeDownload(item.id) },
                onRetry = { DownloadsRepository.retryDownload(item.id) },
                onDelete = { onDeleteDownload(item.id) },
            )
        }
    }

    if (completedShows.isNotEmpty()) {
        item {
            SectionTitle(stringResource(Res.string.downloads_section_shows), completedShows.size)
        }
        items(
            items = completedShows,
            key = { (item, _) -> item.parentMetaId },
        ) { (item, episodes) ->
            DownloadedSeriesCard(
                title = item.title,
                poster = item.poster ?: item.background,
                subtitle = stringResource(Res.string.downloads_episode_count, episodes.size),
                size = formatBytes(episodes.sumOf { it.totalBytes ?: it.downloadedBytes }),
                onClick = { onOpenShow(item.parentMetaId, item.title) },
            )


        }
    }

    if (uiState.items.isEmpty()) {
        item {
            DownloadEmptyState(
                title = stringResource(Res.string.downloads_empty_title),
                subtitle = stringResource(Res.string.downloads_empty_hint),
            )
        }
    }
}

private fun LazyListScope.downloadsMangaContent(
    uiState: MangaDownloadsUiState,
    animeUiState: DownloadsUiState,
    onOpenDownload: (MangaDownloadItem) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    item {
        DownloadsManagementBar(
            summary = mergeStorageSummaries(animeUiState.storageSummary(), uiState.storageSummary()),
            onPauseAll = {
                DownloadsRepository.pauseActiveDownloads()
                MangaDownloadsRepository.pauseActiveDownloads()
            },
            onResumeAll = {
                DownloadsRepository.resumePausedDownloads()
                MangaDownloadsRepository.resumePausedDownloads()
            },
            onRetryAll = {
                DownloadsRepository.retryFailedDownloads()
                MangaDownloadsRepository.retryFailedDownloads()
            },
        )
    }
    val chapterOrder = compareBy<MangaDownloadItem> { it.title.lowercase() }
        .thenBy { it.chapterNumber.toDoubleOrNull() ?: Double.MAX_VALUE }
        .thenBy { it.chapterNumber }
    val groups = listOf(
        Res.string.downloads_section_active to uiState.activeItems.sortedWith(chapterOrder),
        Res.string.downloads_manga_saved to uiState.completedItems.sortedWith(chapterOrder),
    )
    groups.forEach { (title, chapters) ->
        if (chapters.isNotEmpty()) {
            item { SectionTitle(stringResource(title), chapters.size) }
            items(items = chapters, key = { it.id }) { chapter ->
                MangaDownloadRow(
                    item = chapter,
                    onOpen = { onOpenDownload(chapter) },
                    onPause = { MangaDownloadsRepository.pauseDownload(chapter.id) },
                    onResume = { MangaDownloadsRepository.resumeDownload(chapter.id) },
                    onRetry = { MangaDownloadsRepository.retryDownload(chapter.id) },
                    onDelete = { onDeleteDownload(chapter.id) },
                )
            }
        }
    }
    if (uiState.items.isEmpty()) {
        item {
            DownloadEmptyState(
                title = stringResource(Res.string.downloads_manga_empty_title),
                subtitle = stringResource(Res.string.downloads_manga_empty_hint),
                manga = true,
            )
        }
    }
}

private fun mergeStorageSummaries(
    anime: DownloadsStorageSummary,
    manga: DownloadsStorageSummary,
): DownloadsStorageSummary = DownloadsStorageSummary(
    queuedCount = anime.queuedCount + manga.queuedCount,
    activeCount = anime.activeCount + manga.activeCount,
    pausedCount = anime.pausedCount + manga.pausedCount,
    completedCount = anime.completedCount + manga.completedCount,
    failedCount = anime.failedCount + manga.failedCount,
    bytes = anime.bytes + manga.bytes,
)

@Composable
private fun DownloadsManagementBar(
    summary: DownloadsStorageSummary,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryAll: () -> Unit,
) {
    DownloadsOverview(
        summary = summary,
        size = formatBytes(summary.bytes),
        onPauseAll = onPauseAll,
        onResumeAll = onResumeAll,
        onRetryAll = onRetryAll,
    )
}

@Composable
private fun MangaDownloadRow(
    item: MangaDownloadItem,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    DownloadMediaCard(
        title = item.title,
        subtitle = "Chapter ${item.chapterNumber}${item.chapterTitle?.let { " • $it" }.orEmpty()}",
        artwork = item.poster,
        portrait = true,
        status = item.status,
        statusText = mangaDownloadStatusText(item),
        progress = item.progressFraction,
        knownProgress = item.totalPages > 0,
        playable = item.isPlayable,
        onOpen = onOpen,
        onPause = onPause,
        onResume = onResume,
        onRetry = onRetry,
        onDelete = onDelete,
    )
}

private fun mangaDownloadStatusText(item: MangaDownloadItem): String = when (item.status) {
    DownloadStatus.Queued -> "Queued • ${item.downloadedPages}/${item.totalPages} pages"
    DownloadStatus.Downloading -> "Downloading pages ${item.downloadedPages}/${item.totalPages}"
    DownloadStatus.Paused -> "Paused • ${item.downloadedPages}/${item.totalPages} pages"
    DownloadStatus.Completed -> "Downloaded ${item.totalPages} pages"
    DownloadStatus.Failed -> item.errorMessage ?: "Download failed"
}

private fun LazyListScope.downloadsShowContent(
    showId: String,
    episodes: List<DownloadItem>,
    onOpenDownload: (DownloadItem) -> Unit,
    onDeleteDownload: (String) -> Unit,
) {
    val showEpisodes = episodes
        .filter { it.parentMetaId == showId }
        .sortedForSeriesDownloads()

    val seasons = showEpisodes
        .groupBy { it.seasonNumber ?: 0 }
        .toList()
        .sortedWith(
            compareBy<Pair<Int, List<DownloadItem>>> { (season, _) ->
                if (season == 0) 0 else 1
            }.thenBy { (season, _) -> if (season == 0) 0 else season },
        )

    if (seasons.isEmpty()) {
        item {
            DownloadEmptyState(
                title = stringResource(Res.string.downloads_empty_episodes),
                subtitle = stringResource(Res.string.downloads_empty_hint),
            )
        }
        return
    }

    seasons.forEach { (seasonNumber, entries) ->
        item {
            SectionTitle(
                if (seasonNumber == 0) {
                    stringResource(Res.string.episodes_specials)
                } else {
                    stringResource(Res.string.episodes_season, seasonNumber)
                },
                count = entries.size,
            )
        }

        val sortedEpisodes = entries.sortedForSeriesDownloads()

        items(
            items = sortedEpisodes,
            key = { it.id },
        ) { item ->
            DownloadRow(
                item = item,
                onOpen = { onOpenDownload(item) },
                onPause = { DownloadsRepository.pauseDownload(item.id) },
                onResume = { DownloadsRepository.resumeDownload(item.id) },
                onRetry = { DownloadsRepository.retryDownload(item.id) },
                onDelete = { onDeleteDownload(item.id) },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = item.displayTitle()
    DownloadMediaCard(
        title = title,
        subtitle = downloadDisplaySubtitle(item, title),
        artwork = item.episodeThumbnail ?: item.background ?: item.poster,
        portrait = false,
        status = item.status,
        statusText = statusText(item),
        progress = item.progressFraction,
        knownProgress = (item.totalBytes ?: 0L) > 0L,
        playable = item.isPlayable,
        onOpen = onOpen,
        onPause = onPause,
        onResume = onResume,
        onRetry = onRetry,
        onDelete = onDelete,
    )
}

private fun DownloadItem.displayTitle(): String =
    if (isEpisode) {
        episodeTitle?.trim()?.takeIf { it.isNotBlank() } ?: title
    } else {
        title
    }

@Composable
private fun downloadDisplaySubtitle(
    item: DownloadItem,
    displayTitle: String,
): String {
    val seasonNumber = item.seasonNumber
    val episodeNumber = item.episodeNumber
    if (seasonNumber == null || episodeNumber == null) {
        return item.displaySubtitle
    }

    val episodeCode = stringResource(
        Res.string.compose_player_episode_code_full,
        seasonNumber,
        episodeNumber,
    )
    return listOf(
        episodeCode,
        item.episodeTitle?.trim().orEmpty().takeIf { it.isNotBlank() && it != displayTitle },
        item.title.trim().takeIf { it.isNotBlank() && it != displayTitle },
    ).filterNotNull().joinToString(" • ")
}

@Composable
private fun statusText(item: DownloadItem): String {
    val size = if (item.totalBytes != null && item.totalBytes > 0L) {
        "${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}"
    } else {
        formatBytes(item.downloadedBytes)
    }

    return when (item.status) {
        DownloadStatus.Queued -> "Queued • ${formatBytes(item.downloadedBytes)}"
        DownloadStatus.Downloading -> stringResource(Res.string.downloads_status_downloading, size)
        DownloadStatus.Paused -> stringResource(Res.string.downloads_status_paused, size)
        DownloadStatus.Completed -> stringResource(
            Res.string.downloads_status_completed,
            formatBytes(item.totalBytes ?: item.downloadedBytes),
        )
        DownloadStatus.Failed -> item.errorMessage ?: stringResource(Res.string.downloads_status_failed)
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 ${localizedByteUnit("B")}"
    val kib = 1024.0
    val mib = kib * 1024.0
    val gib = mib * 1024.0
    val value = bytes.toDouble()
    return when {
        value >= gib -> "${((value / gib) * 10.0).toInt() / 10.0} ${localizedByteUnit("GB")}"
        value >= mib -> "${((value / mib) * 10.0).toInt() / 10.0} ${localizedByteUnit("MB")}"
        value >= kib -> "${((value / kib) * 10.0).toInt() / 10.0} ${localizedByteUnit("KB")}"
        else -> "$bytes ${localizedByteUnit("B")}"
    }
}
