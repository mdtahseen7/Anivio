package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.anilist.AniListAuthUiState
import com.nuvio.app.features.anilist.AniListConnectionMode
import com.nuvio.app.features.library.LibrarySourceMode
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.CONTINUE_WATCHING_DAYS_CAP_ALL
import com.nuvio.app.features.tracking.CONTINUE_WATCHING_DAYS_CAP_OPTIONS
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingSettingsRepository
import com.nuvio.app.features.tracking.TrackingSettingsUiState
import com.nuvio.app.features.tracking.WatchProgressSource
import com.nuvio.app.features.tracking.effectiveLibrarySourceMode
import com.nuvio.app.features.tracking.effectiveWatchProgressSource
import com.nuvio.app.features.tracking.normalizeContinueWatchingDaysCap
import com.nuvio.app.features.watchprogress.WatchProgressSourceCoordinator
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_retry
import nuvio.composeapp.generated.resources.anilist_source_name
import nuvio.composeapp.generated.resources.settings_tracking_anilist_library_description
import nuvio.composeapp.generated.resources.settings_tracking_anilist_progress_description
import nuvio.composeapp.generated.resources.settings_tracking_connect_first
import nuvio.composeapp.generated.resources.settings_tracking_continue_watching_days_all
import nuvio.composeapp.generated.resources.settings_tracking_continue_watching_days_value
import nuvio.composeapp.generated.resources.settings_tracking_data_sources
import nuvio.composeapp.generated.resources.settings_tracking_local_library_description
import nuvio.composeapp.generated.resources.settings_tracking_local_progress_description
import nuvio.composeapp.generated.resources.settings_tracking_mal_source_description
import nuvio.composeapp.generated.resources.settings_tracking_progress_refresh_failed
import nuvio.composeapp.generated.resources.settings_tracking_services
import nuvio.composeapp.generated.resources.settings_tracking_source_fallback
import nuvio.composeapp.generated.resources.settings_tracking_viewing_discovery
import nuvio.composeapp.generated.resources.tracking_source_local
import nuvio.composeapp.generated.resources.tracking_source_mal
import nuvio.composeapp.generated.resources.tracking_watch_progress_dialog_subtitle
import nuvio.composeapp.generated.resources.trakt_continue_watching_subtitle
import nuvio.composeapp.generated.resources.trakt_continue_watching_window
import nuvio.composeapp.generated.resources.trakt_cw_window_subtitle
import nuvio.composeapp.generated.resources.trakt_cw_window_title
import nuvio.composeapp.generated.resources.trakt_library_source_dialog_subtitle
import nuvio.composeapp.generated.resources.trakt_library_source_dialog_title
import nuvio.composeapp.generated.resources.trakt_library_source_subtitle
import nuvio.composeapp.generated.resources.trakt_library_source_title
import nuvio.composeapp.generated.resources.trakt_watch_progress_dialog_title
import nuvio.composeapp.generated.resources.trakt_watch_progress_subtitle
import nuvio.composeapp.generated.resources.trakt_watch_progress_title
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.trackingSettingsContent(
    isTablet: Boolean,
    aniListUiState: AniListAuthUiState,
    settingsUiState: TrackingSettingsUiState,
) {
    item {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_services),
            isTablet = isTablet,
        ) {
            TrackingProviderCards(
                isTablet = isTablet,
                aniListUiState = aniListUiState,
            )
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_data_sources),
            isTablet = isTablet,
        ) {
            TrackingDataSources(
                isTablet = isTablet,
                settingsUiState = settingsUiState,
                aniListConnected = aniListUiState.mode == AniListConnectionMode.CONNECTED,
            )
        }
    }

    item {
        SettingsSection(
            title = stringResource(Res.string.settings_tracking_viewing_discovery),
            isTablet = isTablet,
        ) {
            TrackingViewingPreferences(
                isTablet = isTablet,
                settingsUiState = settingsUiState,
            )
        }
    }
}

private enum class TrackingDataPicker {
    LIBRARY,
    WATCH_PROGRESS,
}

@Composable
private fun TrackingDataSources(
    isTablet: Boolean,
    settingsUiState: TrackingSettingsUiState,
    aniListConnected: Boolean,
) {
    var activePickerName by rememberSaveable { mutableStateOf<String?>(null) }
    val activePicker = activePickerName?.let(TrackingDataPicker::valueOf)
    val scope = rememberCoroutineScope()
    val transitionState by remember {
        WatchProgressSourceCoordinator.ensureStarted()
        WatchProgressSourceCoordinator.uiState
    }.collectAsStateWithLifecycle()
    val isProviderConnected: (TrackingProviderId) -> Boolean = { provider ->
        provider == TrackingProviderId.ANILIST && aniListConnected
    }
    val effectiveLibrarySource =
        effectiveLibrarySourceMode(settingsUiState.librarySourceMode, isProviderConnected)
    val effectiveProgressSource =
        effectiveWatchProgressSource(settingsUiState.watchProgressSource, isProviderConnected)

    val libraryFallback = if (effectiveLibrarySource != settingsUiState.librarySourceMode) {
        stringResource(
            Res.string.settings_tracking_source_fallback,
            librarySourceModeLabel(settingsUiState.librarySourceMode),
            librarySourceModeLabel(effectiveLibrarySource),
        )
    } else {
        null
    }
    val progressFallback = if (effectiveProgressSource != settingsUiState.watchProgressSource) {
        stringResource(
            Res.string.settings_tracking_source_fallback,
            watchProgressSourceLabel(settingsUiState.watchProgressSource),
            watchProgressSourceLabel(effectiveProgressSource),
        )
    } else {
        null
    }

    SettingsGroup(isTablet = isTablet) {
        TrackingPreferenceActionRow(
            title = stringResource(Res.string.trakt_library_source_title),
            description = stringResource(Res.string.trakt_library_source_subtitle),
            value = librarySourceModeLabel(effectiveLibrarySource),
            supportingMessage = libraryFallback,
            isTablet = isTablet,
            onClick = { activePickerName = TrackingDataPicker.LIBRARY.name },
        )
        SettingsGroupDivider(isTablet = isTablet)
        TrackingPreferenceActionRow(
            title = stringResource(Res.string.trakt_watch_progress_title),
            description = stringResource(Res.string.trakt_watch_progress_subtitle),
            value = watchProgressSourceLabel(effectiveProgressSource),
            supportingMessage = progressFallback,
            isLoading = transitionState.isRefreshing,
            isTablet = isTablet,
            onClick = { activePickerName = TrackingDataPicker.WATCH_PROGRESS.name },
        )
        if (transitionState.lastRefreshSucceeded == false && !transitionState.isRefreshing) {
            SettingsGroupDivider(isTablet = isTablet)
            TrackingInlineErrorRow(
                isTablet = isTablet,
                message = stringResource(Res.string.settings_tracking_progress_refresh_failed),
                onRetry = {
                    scope.launch {
                        WatchProgressSourceCoordinator.refreshActiveSource(ProfileRepository.activeProfileId)
                    }
                },
            )
        }
    }

    when (activePicker) {
        TrackingDataPicker.LIBRARY -> TrackingAdaptivePicker(
            isTablet = isTablet,
            title = stringResource(Res.string.trakt_library_source_dialog_title),
            subtitle = stringResource(Res.string.trakt_library_source_dialog_subtitle),
            selectedValue = effectiveLibrarySource,
            options = librarySourceOptions(aniListConnected),
            onSelected = TrackingSettingsRepository::setLibrarySourceMode,
            onDismiss = { activePickerName = null },
        )
        TrackingDataPicker.WATCH_PROGRESS -> TrackingAdaptivePicker(
            isTablet = isTablet,
            title = stringResource(Res.string.trakt_watch_progress_dialog_title),
            subtitle = stringResource(Res.string.tracking_watch_progress_dialog_subtitle),
            selectedValue = effectiveProgressSource,
            options = watchProgressSourceOptions(aniListConnected),
            onSelected = { source ->
                scope.launch {
                    WatchProgressSourceCoordinator.selectSource(
                        profileId = ProfileRepository.activeProfileId,
                        source = source,
                    )
                }
            },
            onDismiss = { activePickerName = null },
        )
        null -> Unit
    }
}

@Composable
private fun TrackingViewingPreferences(
    isTablet: Boolean,
    settingsUiState: TrackingSettingsUiState,
) {
    var showContinueWatchingPicker by rememberSaveable { mutableStateOf(false) }

    SettingsGroup(isTablet = isTablet) {
        TrackingPreferenceActionRow(
            title = stringResource(Res.string.trakt_continue_watching_window),
            description = stringResource(Res.string.trakt_continue_watching_subtitle),
            value = continueWatchingDaysCapLabel(settingsUiState.continueWatchingDaysCap),
            isTablet = isTablet,
            onClick = { showContinueWatchingPicker = true },
        )
    }

    if (showContinueWatchingPicker) {
        TrackingAdaptivePicker(
            isTablet = isTablet,
            title = stringResource(Res.string.trakt_cw_window_title),
            subtitle = stringResource(Res.string.trakt_cw_window_subtitle),
            selectedValue = normalizeContinueWatchingDaysCap(settingsUiState.continueWatchingDaysCap),
            options = continueWatchingOptions(),
            onSelected = TrackingSettingsRepository::setContinueWatchingDaysCap,
            onDismiss = { showContinueWatchingPicker = false },
        )
    }
}

@Composable
private fun TrackingPreferenceActionRow(
    title: String,
    description: String,
    value: String,
    isTablet: Boolean,
    onClick: () -> Unit,
    supportingMessage: String? = null,
    isLoading: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    SettingsNavigationRow(
        title = title,
        description = listOfNotNull(
            description,
            supportingMessage?.takeIf(String::isNotBlank),
        ).joinToString("\n"),
        isTablet = isTablet,
        trailingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLoading) {
                    NuvioLoadingIndicator(
                        color = tokens.colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.colors.accent,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun TrackingInlineErrorRow(
    isTablet: Boolean,
    message: String,
    onRetry: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isTablet) 20.dp else 16.dp,
                vertical = if (isTablet) 14.dp else 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.colors.danger,
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(Res.string.action_retry))
        }
    }
}

@Composable
private fun librarySourceOptions(
    aniListConnected: Boolean,
): List<TrackingPickerOption<LibrarySourceMode>> {
    val aniListAvailable = isTrackingBrandAvailable(TrackingBrand.ANILIST, aniListConnected)
    return listOf(
        TrackingPickerOption(
            value = LibrarySourceMode.LOCAL,
            title = stringResource(Res.string.tracking_source_local),
            description = stringResource(Res.string.settings_tracking_local_library_description),
        ),
        TrackingPickerOption(
            value = LibrarySourceMode.ANILIST,
            title = stringResource(Res.string.anilist_source_name),
            description = stringResource(Res.string.settings_tracking_anilist_library_description),
            enabled = aniListAvailable,
            unavailableReason = trackingUnavailableReason(TrackingBrand.ANILIST, aniListAvailable),
        ),
        TrackingPickerOption(
            value = LibrarySourceMode.MAL,
            title = stringResource(Res.string.tracking_source_mal),
            description = stringResource(Res.string.settings_tracking_mal_source_description),
            enabled = false,
            unavailableReason = stringResource(Res.string.settings_tracking_mal_source_description),
        ),
    )
}

@Composable
private fun watchProgressSourceOptions(
    aniListConnected: Boolean,
): List<TrackingPickerOption<WatchProgressSource>> {
    val aniListAvailable = isTrackingBrandAvailable(TrackingBrand.ANILIST, aniListConnected)
    return listOf(
        TrackingPickerOption(
            value = WatchProgressSource.LOCAL,
            title = stringResource(Res.string.tracking_source_local),
            description = stringResource(Res.string.settings_tracking_local_progress_description),
        ),
        TrackingPickerOption(
            value = WatchProgressSource.ANILIST,
            title = stringResource(Res.string.anilist_source_name),
            description = stringResource(Res.string.settings_tracking_anilist_progress_description),
            enabled = aniListAvailable,
            unavailableReason = trackingUnavailableReason(TrackingBrand.ANILIST, aniListAvailable),
        ),
        TrackingPickerOption(
            value = WatchProgressSource.MAL,
            title = stringResource(Res.string.tracking_source_mal),
            description = stringResource(Res.string.settings_tracking_mal_source_description),
            enabled = false,
            unavailableReason = stringResource(Res.string.settings_tracking_mal_source_description),
        ),
    )
}

@Composable
private fun continueWatchingOptions(): List<TrackingPickerOption<Int>> =
    CONTINUE_WATCHING_DAYS_CAP_OPTIONS.map { days ->
        TrackingPickerOption(
            value = days,
            title = continueWatchingDaysCapLabel(days),
        )
    }

@Composable
private fun trackingUnavailableReason(
    brand: TrackingBrand,
    isAvailable: Boolean,
): String? = if (isAvailable) {
    null
} else {
    stringResource(Res.string.settings_tracking_connect_first, brand.displayName)
}

@Composable
private fun librarySourceModeLabel(source: LibrarySourceMode): String = when (source) {
    LibrarySourceMode.LOCAL -> stringResource(Res.string.tracking_source_local)
    LibrarySourceMode.ANILIST -> stringResource(Res.string.anilist_source_name)
    LibrarySourceMode.MAL -> stringResource(Res.string.tracking_source_mal)
}

@Composable
private fun watchProgressSourceLabel(source: WatchProgressSource): String = when (source) {
    WatchProgressSource.LOCAL -> stringResource(Res.string.tracking_source_local)
    WatchProgressSource.ANILIST -> stringResource(Res.string.anilist_source_name)
    WatchProgressSource.MAL -> stringResource(Res.string.tracking_source_mal)
}

@Composable
private fun continueWatchingDaysCapLabel(days: Int): String =
    when (val normalized = normalizeContinueWatchingDaysCap(days)) {
        CONTINUE_WATCHING_DAYS_CAP_ALL ->
            stringResource(Res.string.settings_tracking_continue_watching_days_all)
        else ->
            stringResource(Res.string.settings_tracking_continue_watching_days_value, normalized)
    }
