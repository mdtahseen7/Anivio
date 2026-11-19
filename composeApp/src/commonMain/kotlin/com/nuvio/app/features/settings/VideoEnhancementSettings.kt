package com.nuvio.app.features.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.features.player.EnhancementResolution
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsUiState
import com.nuvio.app.features.player.VideoEnhancement
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VideoEnhancementSettings(isTablet: Boolean, settings: PlayerSettingsUiState) {
    var showMode by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.enhancement_title)
    SettingsSection(title = title, isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = stringResource(Res.string.enhancement_mode),
                description = stringResource(settings.videoEnhancement.label),
                enabled = !settings.nvidiaRtxSuperResolutionEnabled,
                isTablet = isTablet,
                onClick = { showMode = true },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = stringResource(Res.string.enhancement_output_title),
                description = stringResource(settings.enhancementResolution.label),
                enabled = !settings.nvidiaRtxSuperResolutionEnabled,
                isTablet = isTablet,
                onClick = { showOutput = true },
            )
        }
    }
    if (showMode) {
        TrackingAdaptivePicker(
            isTablet = isTablet,
            title = title,
            subtitle = stringResource(Res.string.enhancement_limits),
            selectedValue = settings.videoEnhancement,
            options = VideoEnhancement.entries.map { mode ->
                TrackingPickerOption(mode, stringResource(mode.label), stringResource(mode.description))
            },
            onSelected = PlayerSettingsRepository::setVideoEnhancement,
            onDismiss = { showMode = false },
        )
    }
    if (showOutput) {
        TrackingAdaptivePicker(
            isTablet = isTablet,
            title = stringResource(Res.string.enhancement_output_title),
            subtitle = stringResource(settings.enhancementResolution.description),
            selectedValue = settings.enhancementResolution,
            options = EnhancementResolution.entries.map { output ->
                TrackingPickerOption(output, stringResource(output.label), stringResource(output.description))
            },
            onSelected = PlayerSettingsRepository::setEnhancementResolution,
            onDismiss = { showOutput = false },
        )
    }
}
