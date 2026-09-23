package com.nuvio.app.features.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.anilist.AniListAnimeStatistics
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.anilist.AniListAuthRepository
import com.nuvio.app.features.anilist.AniListConnectionMode
import com.nuvio.app.features.anilist.AniListStatisticsRepository
import com.nuvio.app.features.library.LibraryRepository
import com.nuvio.app.features.watched.WatchedRepository
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.stats_center_titles
import nuvio.composeapp.generated.resources.stats_connect_anilist
import nuvio.composeapp.generated.resources.stats_days_watched
import nuvio.composeapp.generated.resources.stats_empty
import nuvio.composeapp.generated.resources.stats_episodes_watched
import nuvio.composeapp.generated.resources.stats_mean_score
import nuvio.composeapp.generated.resources.stats_movies_watched
import nuvio.composeapp.generated.resources.stats_saved_items
import nuvio.composeapp.generated.resources.stats_section_formats
import nuvio.composeapp.generated.resources.stats_section_genres
import nuvio.composeapp.generated.resources.stats_section_release_years
import nuvio.composeapp.generated.resources.stats_section_scores
import nuvio.composeapp.generated.resources.stats_section_status
import nuvio.composeapp.generated.resources.stats_section_studios
import nuvio.composeapp.generated.resources.stats_section_tags
import nuvio.composeapp.generated.resources.stats_series_watched
import nuvio.composeapp.generated.resources.stats_source_anilist
import nuvio.composeapp.generated.resources.stats_source_anivio
import nuvio.composeapp.generated.resources.stats_std_deviation
import nuvio.composeapp.generated.resources.stats_title
import nuvio.composeapp.generated.resources.stats_total_titles
import nuvio.composeapp.generated.resources.stats_watched_items
import org.jetbrains.compose.resources.stringResource

private enum class StatsSource { ANILIST, ANIVIO }

/**
 * Top-level stats screen. A segmented toggle switches between AniList stats (fetched from AniList's
 * server-computed `User.statistics` and drawn as interactive charts) and Anivio stats (from the
 * local watched/library repositories).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statsState by AniListStatisticsRepository.uiState.collectAsStateWithLifecycle()
    val aniListAuth by remember {
        AniListAuthRepository.ensureLoaded()
        AniListAuthRepository.uiState
    }.collectAsStateWithLifecycle()
    val watchedState by remember {
        WatchedRepository.ensureLoaded()
        WatchedRepository.uiState
    }.collectAsStateWithLifecycle()
    val libraryState by remember {
        LibraryRepository.ensureLoaded()
        LibraryRepository.uiState
    }.collectAsStateWithLifecycle()

    var sourceName by rememberSaveable { mutableStateOf(StatsSource.ANILIST.name) }
    val source = remember(sourceName) {
        runCatching { StatsSource.valueOf(sourceName) }.getOrDefault(StatsSource.ANILIST)
    }

    val aniListConnected = aniListAuth.mode == AniListConnectionMode.CONNECTED
    LaunchedEffect(aniListConnected) {
        if (aniListConnected) AniListStatisticsRepository.refresh()
    }

    NuvioScreen(modifier = modifier) {
        stickyHeader {
            NuvioScreenHeader(
                title = stringResource(Res.string.stats_title),
                onBack = onBack,
            )
        }

        item(key = "stats-source-toggle") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                StatsSource.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = entry == source,
                        onClick = { sourceName = entry.name },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = StatsSource.entries.size),
                        label = {
                            Text(
                                when (entry) {
                                    StatsSource.ANILIST -> stringResource(Res.string.stats_source_anilist)
                                    StatsSource.ANIVIO -> stringResource(Res.string.stats_source_anivio)
                                },
                            )
                        },
                    )
                }
            }
        }

        when (source) {
            StatsSource.ANILIST -> aniListStatsContent(
                stats = statsState.statistics?.anime,
                connected = aniListConnected,
                isLoading = statsState.isLoading,
                errorMessage = statsState.errorMessage,
            )
            StatsSource.ANIVIO -> anivioStatsContent(
                watchedItems = watchedState.items.size,
                movies = watchedState.items.count { it.type == "movie" },
                series = watchedState.items.count { it.type != "movie" },
                saved = libraryState.sections.flatMap { it.items }.distinctBy { it.id }.size,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.aniListStatsContent(
    stats: AniListAnimeStatistics?,
    connected: Boolean,
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    if (!connected) {
        item(key = "stats-anilist-disconnected") { StatsMessage(Res.string.stats_connect_anilist) }
        return
    }
    if (isLoading && stats == null) {
        item(key = "stats-anilist-loading") {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        }
        return
    }
    if (stats == null || stats.count == 0) {
        item(key = "stats-anilist-empty") { StatsMessage(Res.string.stats_empty) }
        return
    }

    // Hero tiles.
    item(key = "stats-hero") {
        val days = (stats.minutesWatched / 1440.0)
        StatTileGrid(
            tiles = listOf(
                StatTile(stringResource(Res.string.stats_total_titles), stats.count.toString()),
                StatTile(stringResource(Res.string.stats_episodes_watched), stats.episodesWatched.toString()),
                StatTile(stringResource(Res.string.stats_days_watched), formatCompact(days.toFloat())),
                StatTile(stringResource(Res.string.stats_mean_score), oneDecimal(stats.meanScore)),
            ),
        )
    }

    // Formats donut.
    if (stats.formats.isNotEmpty()) {
        item(key = "stats-formats") {
            StatSectionCard(Res.string.stats_section_formats) {
                DonutChart(
                    data = stats.formats.mapIndexed { i, f ->
                        ChartDatum(f.format.orEmpty(), f.count.toFloat(), paletteColor(i))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    centerPrimary = stats.count.toString(),
                    centerSecondary = stringResource(Res.string.stats_center_titles),
                )
            }
        }
    }

    // Status donut.
    if (stats.statuses.isNotEmpty()) {
        item(key = "stats-status") {
            StatSectionCard(Res.string.stats_section_status) {
                DonutChart(
                    data = stats.statuses.mapIndexed { i, s ->
                        ChartDatum(prettyStatus(s.status), s.count.toFloat(), paletteColor(i))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    centerPrimary = stats.count.toString(),
                    centerSecondary = stringResource(Res.string.stats_center_titles),
                )
            }
        }
    }

    // Release years bars (most recent 12, oldest→newest for readability).
    if (stats.releaseYears.isNotEmpty()) {
        item(key = "stats-years") {
            StatSectionCard(Res.string.stats_section_release_years) {
                VerticalBarChart(
                    data = stats.releaseYears
                        .filter { it.releaseYear != null }
                        .sortedBy { it.releaseYear }
                        .takeLast(12)
                        .map { ChartDatum(it.releaseYear.toString().takeLast(2), it.count.toFloat(), paletteColor(0)) },
                    modifier = Modifier.fillMaxWidth(),
                    barColor = paletteColor(0),
                )
            }
        }
    }

    // Score distribution bars.
    if (stats.scores.isNotEmpty()) {
        item(key = "stats-scores") {
            StatSectionCard(Res.string.stats_section_scores) {
                VerticalBarChart(
                    data = stats.scores
                        .filter { it.score > 0 }
                        .sortedBy { it.score }
                        .map { ChartDatum(it.score.toString(), it.count.toFloat(), paletteColor(3)) },
                    modifier = Modifier.fillMaxWidth(),
                    barColor = paletteColor(3),
                )
            }
        }
    }

    // Top genres.
    if (stats.genres.isNotEmpty()) {
        item(key = "stats-genres") {
            StatSectionCard(Res.string.stats_section_genres) {
                HorizontalBarList(
                    data = stats.genres.take(8).mapIndexed { i, g ->
                        ChartDatum(g.genre.orEmpty(), g.count.toFloat(), paletteColor(i))
                    },
                )
            }
        }
    }

    // Top tags.
    if (stats.tags.isNotEmpty()) {
        item(key = "stats-tags") {
            StatSectionCard(Res.string.stats_section_tags) {
                HorizontalBarList(
                    data = stats.tags.take(8).mapIndexed { i, t ->
                        ChartDatum(t.tag?.name.orEmpty(), t.count.toFloat(), paletteColor(i + 2))
                    },
                )
            }
        }
    }

    // Top studios.
    if (stats.studios.isNotEmpty()) {
        item(key = "stats-studios") {
            StatSectionCard(Res.string.stats_section_studios) {
                HorizontalBarList(
                    data = stats.studios.take(8).mapIndexed { i, s ->
                        ChartDatum(s.studio?.name.orEmpty(), s.count.toFloat(), paletteColor(i + 4))
                    },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.anivioStatsContent(
    watchedItems: Int,
    movies: Int,
    series: Int,
    saved: Int,
) {
    if (watchedItems == 0 && saved == 0) {
        item(key = "stats-anivio-empty") { StatsMessage(Res.string.stats_empty) }
        return
    }
    item(key = "stats-anivio-tiles") {
        StatTileGrid(
            tiles = listOf(
                StatTile(stringResource(Res.string.stats_watched_items), watchedItems.toString()),
                StatTile(stringResource(Res.string.stats_movies_watched), movies.toString()),
                StatTile(stringResource(Res.string.stats_series_watched), series.toString()),
                StatTile(stringResource(Res.string.stats_saved_items), saved.toString()),
            ),
        )
    }
    if (movies + series > 0) {
        item(key = "stats-anivio-donut") {
            StatSectionCard(Res.string.stats_watched_items) {
                DonutChart(
                    data = listOf(
                        ChartDatum(stringResource(Res.string.stats_movies_watched), movies.toFloat(), paletteColor(0)),
                        ChartDatum(stringResource(Res.string.stats_series_watched), series.toFloat(), paletteColor(1)),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    centerPrimary = watchedItems.toString(),
                    centerSecondary = stringResource(Res.string.stats_watched_items),
                )
            }
        }
    }
}

private data class StatTile(val label: String, val value: String)

@Composable
private fun StatTileGrid(tiles: List<StatTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { tile ->
                    NuvioSurfaceCard(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tile.value,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tile.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatSectionCard(
    title: org.jetbrains.compose.resources.StringResource,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(12.dp))
    NuvioSurfaceCard {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun StatsMessage(message: org.jetbrains.compose.resources.StringResource) {
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    )
}

private fun prettyStatus(status: String?): String =
    status?.lowercase()?.replaceFirstChar { it.uppercase() }?.replace('_', ' ') ?: "—"

private fun oneDecimal(value: Double): String {
    val rounded = (value * 10).toLong()
    return "${rounded / 10}.${rounded % 10}"
}
