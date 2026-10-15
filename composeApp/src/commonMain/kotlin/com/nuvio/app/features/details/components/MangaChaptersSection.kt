package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioDropdownChip
import com.nuvio.app.core.ui.NuvioDropdownOption
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.anilist.AniListListRepository
import com.nuvio.app.features.anilist.aniListMediaIdOf
import com.nuvio.app.features.anilist.parseAniListMediaId
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.MangaDownloadItem
import com.nuvio.app.features.downloads.MangaDownloadsRepository
import com.nuvio.app.features.plugins.MangaPluginChapter
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.launch

/** Manga-only: providers are explicitly opt-in via `manga: true` in their manifest. */
@Composable
internal fun MangaChaptersSection(meta: MetaDetails) {
    PluginRepository.initialize()
    val plugins by PluginRepository.uiState.collectAsStateWithLifecycle()
    val providers = remember(plugins.scrapers) { PluginRepository.getEnabledMangaScrapers() }
    // Saved, not just remembered: scrolling the section out of view disposes it, and any
    // plugins-state refresh rebuilds the provider list — both used to silently drop back
    // to the first server. The id re-resolves against whatever providers exist.
    var selectedProviderId by rememberSaveable(meta.id) { mutableStateOf<String?>(null) }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()
    var chapters by remember(meta.id, selectedProvider?.id) { mutableStateOf<List<MangaPluginChapter>>(emptyList()) }
    var loading by remember(meta.id, selectedProvider?.id) { mutableStateOf(false) }
    var sortAscending by rememberSaveable(meta.id) { mutableStateOf(true) }
    var selectedRangeIndex by rememberSaveable(meta.id, selectedProvider?.id) { mutableStateOf(0) }
    var showBulkDownload by rememberSaveable(meta.id, selectedProvider?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // AniList reading progress: chapters at or below it render dimmed.
    val listsUiState by AniListListRepository.uiState.collectAsStateWithLifecycle()
    val mangaDownloads by remember {
        MangaDownloadsRepository.ensureLoaded()
        MangaDownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    LaunchedEffect(meta.id) { AniListListRepository.ensureLoaded() }
    val anilistMediaId = remember(meta.id) { aniListMediaIdOf(meta.id) }
    val readUpTo = remember(listsUiState, anilistMediaId) {
        listsUiState.snapshot.manga.firstOrNull { it.media?.id == anilistMediaId }?.progress ?: 0
    }

    fun load(provider: PluginScraper?) {
        if (provider == null) return
        scope.launch {
            loading = true
            chapters = PluginRepository.getMangaChapters(
                scraper = provider,
                mangaId = parseAniListMediaId(meta.id)?.toString().orEmpty(),
                title = meta.name,
            ).getOrDefault(emptyList())
            loading = false
        }
    }
    LaunchedEffect(selectedProvider?.id, meta.id) { load(selectedProvider) }

    val uniqueChapters = remember(chapters) { chapters.distinctBy(MangaPluginChapter::id) }
    val sortedChapters = remember(uniqueChapters) { sortChaptersAscending(uniqueChapters) }
    val ranges = remember(uniqueChapters) { buildChapterRanges(uniqueChapters) }
    val activeRangeIndex = selectedRangeIndex.takeIf { it in ranges.indices } ?: 0
    val activeRangeChapters = remember(ranges, activeRangeIndex, sortAscending) {
        val (_, rangeChapters) = ranges.getOrNull(activeRangeIndex) ?: return@remember emptyList()
        if (sortAscending) rangeChapters.asReversed() else rangeChapters
    }

    DetailSection(title = "Chapters", showHeader = true) {
        MangaChaptersContent(
            providers = providers,
            selectedProvider = selectedProvider,
            onSelectProvider = { selectedProviderId = it.id },
            ranges = ranges,
            activeRangeIndex = activeRangeIndex,
            onSelectRange = { selectedRangeIndex = it },
            chapters = activeRangeChapters,
            sortAscending = sortAscending,
            onToggleSort = { sortAscending = !sortAscending },
            readUpTo = readUpTo,
            loading = loading,
            parentMetaId = meta.id,
            onSelectChapter = { chapter ->
                selectedProvider?.let { provider ->
                    MangaReaderOverlay.open(
                        title = meta.name,
                        metaId = meta.id,
                        provider = provider,
                        chapters = sortedChapters,
                        chapter = chapter,
                    )
                }
            },
            downloadedItems = mangaDownloads.items,
            onDownloadChapter = { chapter ->
                selectedProvider?.let { provider ->
                    MangaDownloadsRepository.enqueueChapter(
                        parentMetaId = meta.id,
                        title = meta.name,
                        poster = meta.poster,
                        provider = provider,
                        chapter = chapter,
                    )
                }
            },
            onPauseDownload = MangaDownloadsRepository::pauseDownload,
            onResumeDownload = MangaDownloadsRepository::resumeDownload,
            onBulkDownload = { showBulkDownload = true },
        )
    }

    if (showBulkDownload && selectedProvider != null) {
        MangaDownloadSheet(
            meta = meta,
            provider = selectedProvider,
            chapters = sortedChapters,
            onDismiss = { showBulkDownload = false },
        )
    }
}

private fun isChapterRead(chapter: MangaPluginChapter, readUpTo: Int): Boolean =
    chapterNumberOf(chapter)?.let { it <= readUpTo } ?: false

/**
 * Pure presentational content: no repository access, so it renders in
 * Compose previews with fake data and no app build.
 */
@Composable
internal fun MangaChaptersContent(
    providers: List<PluginScraper>,
    selectedProvider: PluginScraper?,
    onSelectProvider: (PluginScraper) -> Unit,
    ranges: List<Pair<ChapterRange, List<MangaPluginChapter>>>,
    activeRangeIndex: Int,
    onSelectRange: (Int) -> Unit,
    chapters: List<MangaPluginChapter>,
    sortAscending: Boolean,
    onToggleSort: () -> Unit,
    readUpTo: Int,
    loading: Boolean,
    parentMetaId: String = "",
    onSelectChapter: (MangaPluginChapter) -> Unit,
    downloadedItems: List<MangaDownloadItem> = emptyList(),
    onDownloadChapter: (MangaPluginChapter) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onResumeDownload: (String) -> Unit = {},
    onBulkDownload: () -> Unit = {},
) {
    if (providers.isEmpty()) {
        Text("Install and enable a manga provider to load chapters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NuvioDropdownChip(
            title = "Choose server",
            label = selectedProvider?.name ?: "Choose server",
            selectedKey = selectedProvider?.id,
            options = providers.map { NuvioDropdownOption(key = it.id, label = it.name) },
            onSelected = { option ->
                providers.firstOrNull { it.id == option.key }?.let(onSelectProvider)
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (ranges.isEmpty()) "–" else "${ranges.sumOf { (_, list) -> list.size }} chapters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        IconButton(onClick = onToggleSort) {
            Icon(
                imageVector = if (sortAscending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = if (sortAscending) "Sort newest first" else "Sort oldest first",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onBulkDownload,
            enabled = selectedProvider != null && chapters.isNotEmpty(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = "Download chapters",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (loading) {
        Text("Loading chapters…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (ranges.isEmpty()) {
        Text("No chapters found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (activeRange, _) = ranges[activeRangeIndex]
    Text(
        text = "CHAPTER RANGE",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ranges.size) { index ->
            val (range, _) = ranges[index]
            FilterChip(
                selected = index == activeRangeIndex,
                onClick = { onSelectRange(index) },
                label = {
                    Text(
                        text = range.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = index == activeRangeIndex,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                ),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
    Text(
        text = "Chapters ${activeRange.label} of ${ranges.sumOf { (_, list) -> list.size }}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chapters.forEach { chapter ->
            MangaChapterRow(
                chapter = chapter,
                read = isChapterRead(chapter, readUpTo),
                onClick = { onSelectChapter(chapter) },
                download = downloadedItems.firstOrNull { item ->
                    item.parentMetaId == parentMetaId &&
                        item.chapterId == chapter.id &&
                        item.providerId == selectedProvider?.id
                },
                onDownload = { onDownloadChapter(chapter) },
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
            )
        }
    }
}

@Composable
private fun MangaChapterRow(
    chapter: MangaPluginChapter,
    read: Boolean,
    onClick: () -> Unit,
    download: MangaDownloadItem?,
    onDownload: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
) {
    val containerColor = if (read) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val titleColor = if (read) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chapter ${chapter.number}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            chapter.title?.takeIf { it.isNotBlank() }?.let { chapterTitle ->
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (download?.status) {
            DownloadStatus.Downloading -> {
                IconButton(onClick = { onPauseDownload(download.id) }) {
                    Icon(Icons.Rounded.Pause, contentDescription = "Pause download")
                }
            }
            DownloadStatus.Paused, DownloadStatus.Failed -> {
                IconButton(onClick = { onResumeDownload(download.id) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Resume download")
                }
            }
            DownloadStatus.Completed -> {
                Icon(Icons.Rounded.Check, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
            }
            null -> {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download chapter")
                }
            }
        }
        if (read && download == null) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Read",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else if (download == null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun previewScraper(id: String, name: String) = PluginScraper(
    id = id,
    repositoryUrl = "https://example.test/manifest.json",
    name = name,
    description = "",
    version = "1.0.0",
    filename = "$id.js",
    supportedTypes = emptyList(),
    enabled = true,
    manifestEnabled = true,
    manga = true,
    code = "",
)

private fun previewChapters(total: Int): List<MangaPluginChapter> =
    (1..total).map { n ->
        MangaPluginChapter(
            id = "ch-$n",
            number = n.toString(),
            title = if (n % 4 == 0) "The usual routine $n" else null,
        )
    }

@Preview
@Composable
private fun MangaChaptersContentPreview() {
    val providers = remember {
        listOf(previewScraper("mangadex", "MangaDex"), previewScraper("mangapill", "MangaPill"))
    }
    val chapters = remember { previewChapters(65) }
    val ranges = remember(chapters) { buildChapterRanges(chapters) }
    NuvioTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                MangaChaptersContent(
                    providers = providers,
                    selectedProvider = providers.first(),
                    onSelectProvider = {},
                    ranges = ranges,
                    activeRangeIndex = 0,
                    onSelectRange = {},
                    chapters = ranges.getOrNull(0)?.second?.asReversed() ?: emptyList(),
                    sortAscending = true,
                    onToggleSort = {},
                    readUpTo = 6,
                    loading = false,
                    onSelectChapter = {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun MangaChaptersContentLoadingPreview() {
    NuvioTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                MangaChaptersContent(
                    providers = listOf(previewScraper("mangadex", "MangaDex")),
                    selectedProvider = null,
                    onSelectProvider = {},
                    ranges = emptyList(),
                    activeRangeIndex = 0,
                    onSelectRange = {},
                    chapters = emptyList(),
                    sortAscending = true,
                    onToggleSort = {},
                    readUpTo = 0,
                    loading = true,
                    onSelectChapter = {},
                )
            }
        }
    }
}
