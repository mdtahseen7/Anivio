package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioModalBottomSheet
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.dismissNuvioBottomSheet
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.debrid.DebridSettingsRepository
import com.nuvio.app.features.debrid.DirectDebridPlayableResult
import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.debrid.toastMessage
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.details.MetaVideo
import com.nuvio.app.features.details.metaVideoSeasonEpisodeComparator
import com.nuvio.app.features.details.normalizeSeasonNumber
import com.nuvio.app.features.details.seasonSortKey
import com.nuvio.app.features.downloads.DownloadSettingsRepository
import com.nuvio.app.features.downloads.DownloadedSubtitle
import com.nuvio.app.features.downloads.DownloadsPlatformDownloader
import com.nuvio.app.features.downloads.DownloadsRepository
import com.nuvio.app.features.downloads.httpDownloadBytes
import com.nuvio.app.features.downloads.saveStreamSubtitles
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerStreamsRepository
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.pluginContentId
import com.nuvio.app.features.streams.StreamAutoPlayMode
import com.nuvio.app.features.streams.StreamAutoPlaySelector
import com.nuvio.app.features.streams.StreamAutoPlaySource
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamSubtitle
import com.nuvio.app.features.streams.toStreamItem
import com.nuvio.app.features.watchprogress.buildPlaybackVideoId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val STREAM_FETCH_TIMEOUT_MS = 25_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDownloadSheet(
    meta: MetaDetails,
    onDismiss: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val downloadsUiState by remember {
        DownloadsRepository.ensureLoaded()
        DownloadsRepository.uiState
    }.collectAsStateWithLifecycle()
    remember {
        DownloadSettingsRepository.ensureLoaded()
        PlayerSettingsRepository.ensureLoaded()
    }

    val downloadedVideoIds = remember(downloadsUiState) {
        downloadsUiState.completedItems.mapNotNullTo(mutableSetOf()) { it.videoId.takeIf { id -> id.isNotBlank() } }
    }

    val groupedEpisodes = remember(meta.videos) {
        meta.videos
            .filter { it.season != null || it.episode != null }
            .sortedWith(metaVideoSeasonEpisodeComparator)
            .groupBy { normalizeSeasonNumber(it.season) }
    }
    val seasons = remember(groupedEpisodes) { groupedEpisodes.keys.sortedBy(::seasonSortKey) }
    var selectedSeason by remember { mutableStateOf(seasons.firstOrNull() ?: 0) }
    val currentEpisodes = groupedEpisodes[selectedSeason].orEmpty()

    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var downloading by remember { mutableStateOf(false) }
    var progressLabel by remember { mutableStateOf<String?>(null) }

    fun videoIdFor(video: MetaVideo): String =
        buildPlaybackVideoId(meta.id, video.season, video.episode, video.id)

    NuvioModalBottomSheet(
        onDismissRequest = { if (!downloading) onDismiss() },
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
                Text(
                    text = stringResource(Res.string.download_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.colors.textPrimary,
                )
                val allSelected = currentEpisodes.isNotEmpty() &&
                    currentEpisodes.all { videoIdFor(it) in selectedIds }
                Text(
                    text = stringResource(Res.string.download_picker_select_all),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (downloading) tokens.colors.textMuted else tokens.colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !downloading) {
                            val ids = currentEpisodes.map { videoIdFor(it) }
                            selectedIds = if (allSelected) selectedIds - ids.toSet() else selectedIds + ids
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }

            if (seasons.size > 1) {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    seasons.forEach { season ->
                        val label = if (season == 0) {
                            stringResource(Res.string.episodes_specials)
                        } else {
                            stringResource(Res.string.episodes_season, season)
                        }
                        val active = season == selectedSeason
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) tokens.colors.background else tokens.colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) tokens.colors.accent else tokens.colors.surfaceElevated)
                                .clickable(enabled = !downloading) { selectedSeason = season }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            if (currentEpisodes.isEmpty()) {
                Text(
                    text = stringResource(Res.string.download_picker_no_episodes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(currentEpisodes, key = { it.id }) { video ->
                        val vid = videoIdFor(video)
                        EpisodeDownloadRow(
                            video = video,
                            selected = vid in selectedIds,
                            alreadyDownloaded = vid in downloadedVideoIds,
                            enabled = !downloading,
                            onToggle = {
                                selectedIds = if (vid in selectedIds) selectedIds - vid else selectedIds + vid
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.size(12.dp))

            val label = progressLabel
            if (downloading && label != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NuvioLoadingIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Button(
                onClick = {
                    if (downloading) return@Button
                    val targets = currentEpisodes.filter { videoIdFor(it) in selectedIds }
                    if (targets.isEmpty()) return@Button
                    downloading = true
                    scope.launch {
                        downloadEpisodes(
                            meta = meta,
                            episodes = targets,
                            onProgress = { progressLabel = it },
                        )
                        downloading = false
                        progressLabel = null
                        dismissNuvioBottomSheet(sheetState, onDismiss)
                    }
                },
                enabled = !downloading && selectedIds.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = nuvioSafeBottomPadding(12.dp)),
            ) {
                Text(
                    text = stringResource(
                        Res.string.download_picker_download_selected,
                        selectedIds.size,
                    ),
                )
            }
        }
    }
}

@Composable
private fun EpisodeDownloadRow(
    video: MetaVideo,
    selected: Boolean,
    alreadyDownloaded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val code = buildString {
        video.season?.let { append("S").append(it).append(" ") }
        video.episode?.let { append("E").append(it) }
    }.trim()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) tokens.colors.accent else tokens.colors.textMuted,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = listOf(code, video.title).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyLarge,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (alreadyDownloaded) {
            Icon(
                imageVector = Icons.Rounded.DownloadDone,
                contentDescription = null,
                tint = tokens.colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private suspend fun downloadEpisodes(
    meta: MetaDetails,
    episodes: List<MetaVideo>,
    onProgress: (String) -> Unit,
) {
    val debrid = DebridSettingsRepository.snapshot()
    val installedAddonNames = AddonRepository.uiState.value.addons
        .enabledAddons()
        .map { it.displayTitle }
        .toSet()
    // Default download server is a locally-installed plugin (scraper). When one is selected, episodes
    // are fetched by running ONLY that plugin (never other plugins/addons). When unset, fall back to
    // loading from all sources and picking the first.
    val defaultPluginKey = DownloadSettingsRepository.uiState.value.defaultServerAddonName?.takeIf { it.isNotBlank() }
    val defaultScraper = defaultPluginKey?.let { key ->
        PluginRepository.uiState.value.scrapers.firstOrNull { it.enabled && (it.name == key || it.id == key) }
    }

    for (video in episodes) {
        val season = video.season
        val episode = video.episode
        onProgress(getString(Res.string.download_picker_fetching_streams))

        val videoId = buildPlaybackVideoId(meta.id, season, episode, video.id)
        val allStreams: List<StreamItem> = if (defaultScraper != null) {
            PluginRepository.executeScraper(
                scraper = defaultScraper,
                tmdbId = pluginContentId(videoId = videoId, season = season, episode = episode),
                mediaType = meta.type,
                season = season,
                episode = episode,
            ).fold(
                onSuccess = { results ->
                    results.map { result ->
                        result.toStreamItem(
                            scraper = defaultScraper,
                            addonName = defaultScraper.name,
                            addonId = "plugin:${defaultScraper.id}",
                            includeScraperNameInSubtitle = false,
                        )
                    }
                },
                onFailure = { emptyList() },
            )
        } else {
            PlayerStreamsRepository.loadEpisodeStreams(
                type = meta.type,
                videoId = videoId,
                season = season,
                episode = episode,
            )
            val finalState = withTimeoutOrNull(STREAM_FETCH_TIMEOUT_MS) {
                PlayerStreamsRepository.episodeStreamsState.first { state ->
                    !state.isAnyLoading && (state.groups.any { it.streams.isNotEmpty() } || state.emptyStateReason != null)
                }
            }
            finalState?.groups?.flatMap { it.streams }.orEmpty()
        }

        val picked: StreamItem? = StreamAutoPlaySelector.selectAutoPlayStream(
            streams = allStreams,
            mode = StreamAutoPlayMode.FIRST_STREAM,
            regexPattern = "",
            // Streams are already scoped to the chosen plugin (or all sources), so no extra filtering.
            source = StreamAutoPlaySource.ALL_SOURCES,
            installedAddonNames = installedAddonNames,
            selectedAddons = emptySet(),
            selectedPlugins = emptySet(),
            debridEnabled = debrid.canResolvePlayableLinks,
            activeResolverProviderId = debrid.activeResolverProviderId,
        )

        if (picked == null) {
            NuvioToastController.show(
                getString(
                    Res.string.download_picker_no_stream_found,
                    season ?: 0,
                    episode ?: 0,
                ),
            )
            continue
        }

        val resolvedStream: StreamItem? = if (DirectDebridPlaybackResolver.shouldResolveToPlayableStream(picked)) {
            when (val resolved = DirectDebridPlaybackResolver.resolveToPlayableStream(picked, season, episode)) {
                is DirectDebridPlayableResult.Success -> resolved.stream
                else -> {
                    resolved.toastMessage()?.let { NuvioToastController.show(it) }
                    null
                }
            }
        } else {
            picked
        }

        if (resolvedStream == null) continue

        // Save any subtitle tracks the stream carries next to the video, for offline playback.
        val downloadedSubtitles = saveStreamSubtitles(
            subtitles = resolvedStream.externalSubtitles,
            baseFileName = buildString {
                append(meta.id.filter { it.isLetterOrDigit() }.take(40))
                season?.let { append("_s").append(it) }
                episode?.let { append("_e").append(it) }
            },
        )

        val result = DownloadsRepository.enqueueFromStream(
            contentType = meta.type,
            videoId = videoId,
            parentMetaId = meta.id,
            parentMetaType = meta.type,
            title = meta.name,
            logo = meta.logo,
            poster = meta.poster,
            background = meta.background,
            seasonNumber = season,
            episodeNumber = episode,
            episodeTitle = video.title,
            episodeThumbnail = video.thumbnail,
            stream = resolvedStream,
            subtitles = downloadedSubtitles,
        )
        NuvioToastController.show(result.toastMessage())
    }
    PlayerStreamsRepository.clearEpisodeStreams()
}
