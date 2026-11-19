package com.nuvio.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.PlayerSettingsUiState

@Composable
internal fun DesktopAudioPlaybackSettings(isTablet: Boolean, settings: PlayerSettingsUiState) {
    SettingsSection(title = "Audio adjustments", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            AudioTuningSlider("Audio delay", settings.audioDelayMs.toFloat(), -2000f..2000f, "ms") {
                PlayerSettingsRepository.setAudioDelayMs((it / 50).toInt() * 50)
            }
            SettingsGroupDivider(isTablet = isTablet)
            AudioTuningSlider("Audio boost", settings.audioAmplificationDb, 0f..12f, "dB") {
                PlayerSettingsRepository.setAudioAmplificationDb(it.toInt().toFloat())
            }
        }
    }
}

@Composable
private fun AudioTuningSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onChanged: (Float) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("$title · ${draft.toInt()} $unit", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChanged(draft) },
            valueRange = range,
        )
    }
}
