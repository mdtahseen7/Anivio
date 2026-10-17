package com.nuvio.app.features.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.downloads.DownloadStatus
import com.nuvio.app.features.downloads.MangaDownloadItem
import com.nuvio.app.features.downloads.MangaDownloadsRepository
import com.nuvio.app.features.plugins.MangaPluginChapter
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaDownloadSheet(
    meta: MetaDetails,
    provider: PluginScraper,
    chapters: List<MangaPluginChapter>,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val downloadsUiState by remember {
        MangaDownloadsRepository.ensureLoaded()
        MangaDownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    val uniqueChapters = remember(chapters) { chapters.distinctBy(MangaPluginChapter::id) }
    val downloadedByChapter = remember(downloadsUiState.items, meta.id, provider.id) {
        downloadsUiState.items
            .filter { it.parentMetaId == meta.id && it.providerId == provider.id }
            .associateBy(MangaDownloadItem::chapterId)
    }
    var selectedIds by remember(uniqueChapters) { mutableStateOf(emptySet<String>()) }
    var queueing by remember { mutableStateOf(false) }
    var progressLabel by remember { mutableStateOf<String?>(null) }
    val allSelected = uniqueChapters.isNotEmpty() && uniqueChapters.all { it.id in selectedIds }
    val selectedToQueue = uniqueChapters.filter { chapter ->
        chapter.id in selectedIds && downloadedByChapter[chapter.id]?.status != DownloadStatus.Completed
    }

    NuvioModalBottomSheet(
        onDismissRequest = { if (!queueing) onDismiss() },
        sheetState = sheetState,
        fullHeight = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.screenHorizontal),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download manga chapters",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.colors.textPrimary,
                    )
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.colors.textSecondary,
                    )
                }
                Text(
                    text = if (allSelected) "Clear all" else "Select all",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (queueing) tokens.colors.textMuted else tokens.colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !queueing) {
                            selectedIds = if (allSelected) emptySet() else uniqueChapters.mapTo(mutableSetOf(), MangaPluginChapter::id)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            if (uniqueChapters.isEmpty()) {
                Text(
                    text = "No chapters available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(uniqueChapters, key = MangaPluginChapter::id) { chapter ->
                        MangaBulkDownloadRow(
                            chapter = chapter,
                            selected = chapter.id in selectedIds,
                            download = downloadedByChapter[chapter.id],
                            enabled = !queueing,
                            onToggle = {
                                selectedIds = if (chapter.id in selectedIds) {
                                    selectedIds - chapter.id
                                } else {
                                    selectedIds + chapter.id
                                }
                            },
                        )
                    }
                }
            }

            progressLabel?.let { label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (queueing || selectedToQueue.isEmpty()) return@Button
                    queueing = true
                    scope.launch {
                        selectedToQueue.forEachIndexed { index, chapter ->
                            MangaDownloadsRepository.enqueueChapter(
                                parentMetaId = meta.id,
                                title = meta.name,
                                poster = meta.poster,
                                provider = provider,
                                chapter = chapter,
                            )
                            progressLabel = "Queued ${index + 1} of ${selectedToQueue.size} chapters"
                        }
                        queueing = false
                        dismissNuvioBottomSheet(sheetState, onDismiss)
                    }
                },
                enabled = !queueing && selectedToQueue.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text("Download ${selectedToQueue.size} selected")
            }
        }
    }
}

@Composable
private fun MangaBulkDownloadRow(
    chapter: MangaPluginChapter,
    selected: Boolean,
    download: MangaDownloadItem?,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chapter ${chapter.number}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            chapter.title?.takeIf(String::isNotBlank)?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (download?.status) {
            DownloadStatus.Queued -> Text(
                text = "Queued",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DownloadStatus.Completed -> Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
            )
            DownloadStatus.Downloading -> Text(
                text = "${download.downloadedPages}/${download.totalPages}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DownloadStatus.Paused, DownloadStatus.Failed -> Text(
                text = "Resume",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            null -> Unit
        }
    }
}
