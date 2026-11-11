package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import com.nuvio.app.features.player.rememberAudioOutputKey
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsUiState

@Composable
internal fun AudioPlaybackSettings(isTablet: Boolean, settings: PlayerSettingsUiState) {
    var showChannels by remember { mutableStateOf(false) }
    val outputKey = rememberAudioOutputKey(settings.rememberAudioDelay)
    LaunchedEffect(outputKey, settings.rememberAudioDelay) { PlayerSettingsRepository.setActiveAudioDevice(outputKey) }
    if (showChannels) AlertDialog(onDismissRequest = { showChannels = false }, title = { Text("Output channels") }, text = {
        Column { listOf("mono" to "Mono", "stereo" to "Stereo", "5.1" to "5.1 surround", "7.1" to "7.1 surround").forEach { (value, label) ->
            TextButton(onClick = { PlayerSettingsRepository.setAudioChannels(value); showChannels = false }) { Text(label) }
        } }
    }, confirmButton = { TextButton(onClick = { showChannels = false }) { Text("Close") } })
    SettingsSection(title = "Audio processing", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(title = "Remember audio delay", description = "Save audio sync separately for each output device. Temporary adjustments are available in the player’s audio menu.", checked = settings.rememberAudioDelay, isTablet = isTablet, onCheckedChange = PlayerSettingsRepository::setRememberAudioDelay)
            if (settings.rememberAudioDelay) AudioValueSlider("Audio delay", settings.audioDelayMs.toFloat(), -2000f..2000f, "${settings.audioDelayMs} ms") { PlayerSettingsRepository.setAudioDelayMs(it.toInt()) }
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(title = "Remember amplification", description = "Restore amplification for new playback sessions. Temporary adjustments are available in the player’s audio menu.", checked = settings.persistAudioAmplification, isTablet = isTablet, onCheckedChange = PlayerSettingsRepository::setPersistAudioAmplification)
            if (settings.persistAudioAmplification) AudioValueSlider("Amplification", settings.audioAmplificationDb, 0f..20f, "${settings.audioAmplificationDb.toInt()} dB", PlayerSettingsRepository::setAudioAmplificationDb)
            SettingsGroupDivider(isTablet)
            SettingsSwitchRow(title = "Downmix audio", description = "Mix surround audio to the selected speaker layout. Applies when starting playback.", checked = settings.audioDownmix, isTablet = isTablet, onCheckedChange = PlayerSettingsRepository::setAudioDownmix)
            if (settings.audioDownmix) {
                SettingsNavigationRow(title = "Output channels", description = settings.audioChannels.let { if (it == "auto") "Stereo" else it }, isTablet = isTablet, onClick = { showChannels = true })
                SettingsSwitchRow(title = "Maintain original volume", description = "Keep the original mix level instead of reducing it to prevent clipping.", checked = settings.audioMaintainVolume, isTablet = isTablet, onCheckedChange = PlayerSettingsRepository::setAudioMaintainVolume)
                AudioValueSlider("Center mix level", settings.audioCenterMixLevel, 0f..1.5f, "${(settings.audioCenterMixLevel * 100).toInt()}%", PlayerSettingsRepository::setAudioCenterMixLevel)
            }
        }
    }
}

@Composable
private fun AudioValueSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("$title · $label")
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
    }
}
