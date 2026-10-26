package com.nuvio.app.features.schedule

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import nuvio.composeapp.generated.resources.schedule_airing
import nuvio.composeapp.generated.resources.schedule_loading
import nuvio.composeapp.generated.resources.schedule_no_releases_day
import nuvio.composeapp.generated.resources.schedule_releases_count
import nuvio.composeapp.generated.resources.schedule_refresh
import nuvio.composeapp.generated.resources.schedule_title
import nuvio.composeapp.generated.resources.schedule_this_week
import org.jetbrains.compose.resources.stringResource

/**
 * Weekly airing schedule. Data comes from AniList's `airingSchedules` — the same source AniChart
 * and LiveChart render. The page presents a complete seven-day calendar and a focused day timeline.
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
        buildScheduleCalendarDays(uiState.entries, LibraryScheduleClock.nowEpochMs())
    }
    var selectedDayStart by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(scheduleGroups) {
        val availableDay = scheduleGroups.firstOrNull { it.startEpochSec == selectedDayStart }
            ?: scheduleGroups.firstOrNull()
        selectedDayStart = availableDay?.startEpochSec
    }
    val selectedGroup = scheduleGroups.firstOrNull { it.startEpochSec == selectedDayStart }
        ?: scheduleGroups.firstOrNull()

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
            uiState.isLoading && uiState.entries.isEmpty() -> item(key = "schedule-loading") {
                ScheduleMessage(text = stringResource(Res.string.schedule_loading))
            }
            uiState.error && uiState.entries.isEmpty() -> item(key = "schedule-error") {
                ScheduleMessage(text = stringResource(Res.string.schedule_error))
            }
            !uiState.isLoading && uiState.entries.isEmpty() -> item(key = "schedule-empty") {
                ScheduleMessage(text = stringResource(Res.string.schedule_empty))
            }
            else -> scheduleGroups(
                groups = scheduleGroups,
                selectedGroup = selectedGroup,
                onDaySelected = { selectedDayStart = it },
                onEntryClick = onEntryClick,
            )
        }
    }
}

private fun LazyListScope.scheduleGroups(
    groups: List<ScheduleDayGroup>,
    selectedGroup: ScheduleDayGroup?,
    onDaySelected: (Long) -> Unit,
    onEntryClick: (ScheduleEntry) -> Unit,
) {
    item(key = "schedule-calendar") {
        ScheduleCalendarCard(
            groups = groups,
            selectedDayStart = selectedGroup?.startEpochSec,
            onDaySelected = onDaySelected,
        )
    }
    item(key = "schedule-selected-header") {
        selectedGroup?.let { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.schedule_releases_count, group.entries.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = ScheduleTime.monthYearLabel(group.startEpochSec),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (selectedGroup == null || selectedGroup.entries.isEmpty()) {
        item(key = "schedule-selected-empty") {
            ScheduleMessage(text = stringResource(Res.string.schedule_no_releases_day))
        }
    } else {
        items(count = selectedGroup.entries.size, key = { index ->
            val entry = selectedGroup.entries[index]
            "schedule-entry:${entry.mediaId}:${entry.episodeNumber}:${entry.airingAtEpochSec}"
        }) { index ->
            val entry = selectedGroup.entries[index]
            ScheduleEntryCard(entry = entry, onClick = { onEntryClick(entry) })
        }
    }
}

@Composable
private fun ScheduleCalendarCard(
    groups: List<ScheduleDayGroup>,
    selectedDayStart: Long?,
    onDaySelected: (Long) -> Unit,
) {
    val days = groups.take(7)
    val totalEntries = days.sumOf { it.entries.size }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.schedule_this_week),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    groups.firstOrNull()?.let { group ->
                        Text(
                            text = ScheduleTime.monthYearLabel(group.startEpochSec),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.schedule_releases_count, totalEntries),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                days.forEach { group ->
                    ScheduleDayCell(
                        group = group,
                        selected = group.startEpochSec == selectedDayStart,
                        modifier = Modifier.weight(1f),
                        onClick = { onDaySelected(group.startEpochSec) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayCell(
    group: ScheduleDayGroup,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        },
        label = "schedule-day-background",
    )
    val foregroundColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = if (group.isToday) "TODAY" else ScheduleTime.weekdayShortLabel(group.startEpochSec).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = foregroundColor.copy(alpha = 0.78f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = ScheduleTime.dayNumber(group.startEpochSec),
            style = MaterialTheme.typography.titleLarge,
            color = foregroundColor,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = group.entries.size.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = foregroundColor.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun ScheduleEntryCard(
    entry: ScheduleEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(72.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            AsyncImage(
                model = entry.imageUrl,
                contentDescription = entry.title,
                modifier = Modifier
                    .size(width = 52.dp, height = 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.episodeNumber?.let { episode ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Episode $episode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatScheduleTime(entry.airingAtEpochSec),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.schedule_airing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
