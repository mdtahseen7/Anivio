package com.nuvio.app.features.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import com.nuvio.app.features.updater.UpdateChannel
import com.nuvio.app.features.updater.UpdatePreferences

@Composable
internal fun UpdateChannelSettingsRow(isTablet: Boolean) {
    val channel by UpdatePreferences.channel.collectAsState()
    var show by remember { mutableStateOf(false) }
    SettingsNavigationRow(title = "Update channel", icon = Icons.Rounded.SystemUpdate, description = if (channel == UpdateChannel.STABLE) "Stable · released updates" else "Beta · includes preview releases", isTablet = isTablet, onClick = { show = true })
    if (show) AlertDialog(
        onDismissRequest = { show = false }, title = { Text("Update channel") },
        text = { Text("Stable receives released versions. Beta also receives preview releases, which may contain unfinished features.") },
        confirmButton = { TextButton(onClick = { UpdatePreferences.setChannel(UpdateChannel.BETA); show = false }) { Text("Beta") } },
        dismissButton = { TextButton(onClick = { UpdatePreferences.setChannel(UpdateChannel.STABLE); show = false }) { Text("Stable") } },
    )
}
