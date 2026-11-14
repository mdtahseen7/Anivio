package com.nuvio.app.features.downloads

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nuvio.app.navigation.LocalNativeNavigationBarHidden
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.navigation.LocalUseNativeNavigation
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DownloadsHeader(title: String, onBack: () -> Unit, onOpenFolder: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    val nativeHeader = LocalUseNativeNavigation.current && !LocalNativeNavigationBarHidden.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.background)
            .then(if (nativeHeader) Modifier else Modifier.statusBarsPadding())
            .padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!nativeHeader) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.action_back), tint = colors.textPrimary)
            }
            Text(
                title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Surface(shape = CircleShape, color = colors.surface) {
            IconButton(onClick = onOpenFolder) {
                Icon(Icons.Rounded.FolderOpen, stringResource(Res.string.downloads_open_directory), tint = colors.textPrimary)
            }
        }
    }
}

@Composable
internal fun DownloadsMediaTabs(selectedIndex: Int, onSelected: (Int) -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(colors.surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(
            stringResource(Res.string.downloads_tab_anime) to Icons.Rounded.PlayArrow,
            stringResource(Res.string.downloads_tab_manga) to Icons.AutoMirrored.Rounded.MenuBook,
        ).forEachIndexed { index, (label, icon) ->
            val background by animateColorAsState(if (selectedIndex == index) colors.accent else Color.Transparent)
            Surface(
                onClick = { onSelected(index) }, modifier = Modifier.weight(1f),
                shape = CircleShape, color = background,
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val foreground = if (selectedIndex == index) MaterialTheme.colorScheme.onPrimary else colors.textSecondary
                    Icon(icon, null, Modifier.size(20.dp), tint = foreground)
                    Text(label, color = foreground, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun DownloadsOverview(
    summary: DownloadsStorageSummary,
    size: String,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryAll: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(48.dp).background(colors.accent.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.DownloadForOffline, null, tint = colors.accent) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(Res.string.downloads_offline_library), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(stringResource(Res.string.downloads_saved_count, summary.completedCount), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(size, style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
                    Text(stringResource(Res.string.downloads_size_label), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                val pending = summary.activeCount + summary.queuedCount + summary.pausedCount + summary.failedCount
                if (pending > 0) {
                    Surface(shape = CircleShape, color = colors.accent.copy(alpha = 0.1f)) {
                        Text(
                            stringResource(Res.string.downloads_pending_count, pending),
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium, color = colors.accent,
                        )
                    }
                }
            }
            if (summary.activeCount + summary.queuedCount + summary.pausedCount + summary.failedCount > 0) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (summary.activeCount + summary.queuedCount > 0) {
                        DownloadBulkAction(Icons.Rounded.Pause, stringResource(Res.string.downloads_pause_all), onPauseAll)
                    }
                    if (summary.pausedCount > 0) {
                        DownloadBulkAction(Icons.Rounded.PlayArrow, stringResource(Res.string.downloads_resume_all), onResumeAll)
                    }
                    if (summary.failedCount > 0) {
                        DownloadBulkAction(Icons.Rounded.Refresh, stringResource(Res.string.downloads_retry_all), onRetryAll)
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadBulkAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    Surface(onClick = onClick, shape = CircleShape, color = colors.surfaceElevated) {
        Row(
            Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.textPrimary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
        }
    }
}

@Composable
internal fun DefaultServerRow(serverName: String?, onClick: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = colors.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Dns, null, Modifier.size(22.dp), tint = colors.textSecondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(Res.string.download_default_server), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                Text(serverName?.takeIf(String::isNotBlank) ?: stringResource(Res.string.download_default_server_none),
                    style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.textSecondary)
        }
    }
}

@Composable
internal fun SectionTitle(title: String, count: Int? = null) {
    val colors = MaterialTheme.nuvio.colors
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
    }
}

@Composable
internal fun DownloadEmptyState(title: String, subtitle: String, manga: Boolean = false) {
    val colors = MaterialTheme.nuvio.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(80.dp).background(colors.surface, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(if (manga) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.Download,
                null, Modifier.size(34.dp), tint = colors.accent)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun DownloadedSeriesCard(title: String, poster: String?, subtitle: String, size: String, onClick: () -> Unit) {
    val colors = MaterialTheme.nuvio.colors
    Surface(onClick = onClick, color = colors.surface, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DownloadArtwork(poster, portrait = true, manga = false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Text(size, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = colors.textSecondary)
        }
    }
}

@Composable
internal fun DownloadMediaCard(
    title: String, subtitle: String, artwork: String?, portrait: Boolean,
    status: DownloadStatus, statusText: String, progress: Float, knownProgress: Boolean,
    playable: Boolean, onOpen: () -> Unit, onPause: () -> Unit, onResume: () -> Unit,
    onRetry: () -> Unit, onDelete: () -> Unit,
) {
    val colors = MaterialTheme.nuvio.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val progressValue by animateFloatAsState(progress.coerceIn(0f, 1f))
    val statusColor = when (status) {
        DownloadStatus.Failed -> MaterialTheme.colorScheme.error
        DownloadStatus.Downloading, DownloadStatus.Completed -> colors.accent
        else -> colors.textSecondary
    }
    Surface(onClick = onOpen, enabled = playable, shape = RoundedCornerShape(24.dp), color = colors.surface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                DownloadArtwork(artwork, portrait, manga = portrait)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(title, Modifier.weight(1f).padding(top = 3.dp), style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.MoreVert, stringResource(Res.string.downloads_item_actions), tint = colors.textSecondary)
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_delete)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                    onClick = { menuExpanded = false; onDelete() },
                                )
                            }
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                Text(statusText, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = statusColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (knownProgress && status in listOf(DownloadStatus.Downloading, DownloadStatus.Paused)) {
                    Text("${(progress * 100).toInt().coerceIn(0, 100)}%", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                }
                val icon = when (status) {
                    DownloadStatus.Downloading, DownloadStatus.Queued -> Icons.Rounded.Pause
                    DownloadStatus.Failed -> Icons.Rounded.Refresh
                    else -> if (portrait && playable) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.PlayArrow
                }
                val label = when (status) {
                    DownloadStatus.Downloading, DownloadStatus.Queued -> stringResource(Res.string.compose_action_pause)
                    DownloadStatus.Paused -> stringResource(Res.string.action_resume)
                    DownloadStatus.Failed -> stringResource(Res.string.action_retry)
                    else -> stringResource(if (portrait) Res.string.downloads_read else Res.string.action_play)
                }
                Surface(
                    onClick = when (status) {
                        DownloadStatus.Downloading, DownloadStatus.Queued -> onPause
                        DownloadStatus.Paused -> onResume
                        DownloadStatus.Failed -> onRetry
                        else -> onOpen
                    },
                    enabled = status != DownloadStatus.Completed || playable,
                    shape = CircleShape,
                    color = if (status == DownloadStatus.Completed) colors.accent else colors.surfaceElevated,
                ) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, label, Modifier.size(22.dp), tint = if (status == DownloadStatus.Completed) MaterialTheme.colorScheme.onPrimary else colors.textPrimary)
                    }
                }
            }
            if (status in listOf(DownloadStatus.Downloading, DownloadStatus.Paused, DownloadStatus.Queued)) {
                val track = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                if (knownProgress || status != DownloadStatus.Downloading) {
                    LinearProgressIndicator(progress = { progressValue }, modifier = track,
                        color = if (status == DownloadStatus.Downloading) colors.accent else colors.textMuted,
                        trackColor = colors.surfaceElevated, drawStopIndicator = {})
                } else {
                    LinearProgressIndicator(modifier = track, color = colors.accent, trackColor = colors.surfaceElevated)
                }
            }
        }
    }
}

@Composable
private fun DownloadArtwork(url: String?, portrait: Boolean, manga: Boolean) {
    val colors = MaterialTheme.nuvio.colors
    Box(
        Modifier.size(width = if (portrait) 64.dp else 88.dp, height = if (portrait) 90.dp else 72.dp)
            .clip(RoundedCornerShape(12.dp)).background(colors.surfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        Icon(if (manga) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.PlayArrow, null, tint = colors.textMuted)
        if (!url.isNullOrBlank()) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
