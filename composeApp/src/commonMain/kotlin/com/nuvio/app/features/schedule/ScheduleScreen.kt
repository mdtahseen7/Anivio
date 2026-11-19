package com.nuvio.app.features.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.schedule_empty
import nuvio.composeapp.generated.resources.schedule_error
import nuvio.composeapp.generated.resources.schedule_refresh
import nuvio.composeapp.generated.resources.schedule_title
import org.jetbrains.compose.resources.stringResource

/**
 * Weekly airing schedule. Data comes from AniList's `airingSchedules` — the same source AniChart
 * and LiveChart render. Entries are grouped by local weekday with "today" pinned first.
 */
@Composable
fun ScheduleScreen(
    onBack: () -> Unit,
    onEntryClick: (ScheduleEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by remember {
        ScheduleRepository.ensureLoaded()
        ScheduleRepository.uiState
    }.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val scheduleGroups = remember(uiState.entries) {
        buildScheduleGroups(uiState.entries, LibraryScheduleClock.nowEpochMs())
    }

    NuvioScreen(
        modifier = modifier.fillMaxSize(),
    ) {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.schedule_title),
                onBack = onBack,
                actions = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { scope.launch { ScheduleRepository.refresh() } }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(Res.string.schedule_refresh),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        }

        when {
            uiState.error && uiState.entries.isEmpty() -> item(key = "schedule-error") {
                ScheduleMessage(text = stringResource(Res.string.schedule_error))
            }
            !uiState.isLoading && uiState.entries.isEmpty() -> item(key = "schedule-empty") {
                ScheduleMessage(text = stringResource(Res.string.schedule_empty))
            }
            else -> scheduleGroups(
                groups = scheduleGroups,
                onEntryClick = onEntryClick,
            )
        }
    }
}

private fun LazyListScope.scheduleGroups(
    groups: List<ScheduleDayGroup>,
    onEntryClick: (ScheduleEntry) -> Unit,
) {
    groups.forEach { group ->
        item(key = "schedule-day:${group.startEpochSec}") {
            Text(
                text = group.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (group.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(count = group.entries.size, key = { index ->
            val entry = group.entries[index]
            "schedule-entry:${entry.mediaId}:${entry.episodeNumber}:${entry.airingAtEpochSec}"
        }) { index ->
            val entry = group.entries[index]
            ScheduleEntryRow(entry = entry, onClick = { onEntryClick(entry) })
        }
    }
}

@Composable
private fun ScheduleEntryRow(
    entry: ScheduleEntry,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = entry.imageUrl,
            contentDescription = entry.title,
            modifier = Modifier
                .size(width = 52.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.episodeNumber?.let { episode ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Episode $episode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = formatScheduleTime(entry.airingAtEpochSec),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduleMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
