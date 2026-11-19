package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nuvio.app.core.ui.NuvioStatusModal
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.profiles.ProfileRepository

@Composable
internal fun BackupSettings(isTablet: Boolean) {
    val profileId = ProfileRepository.activeProfileId
    var exportRequest by remember { mutableIntStateOf(0) }
    var importRequest by remember { mutableIntStateOf(0) }
    var exportPayload by remember { mutableStateOf("") }
    var awaitingRestore by remember(profileId) { mutableStateOf<DesktopBackup?>(null) }
    SettingsSection(title = "Backup and restore", isTablet = isTablet) {
        SettingsGroup(isTablet = isTablet) {
            SettingsNavigationRow(
                title = "Export backup",
                description = "Save library, collections, and watch history as a JSON file.",
                isTablet = isTablet,
                onClick = {
                    exportPayload = BackupRepository.export()
                    exportRequest++
                },
            )
            SettingsGroupDivider(isTablet = isTablet)
            SettingsNavigationRow(
                title = "Restore backup",
                description = "Load profile data from an Anivio backup file.",
                isTablet = isTablet,
                onClick = { importRequest++ },
            )
        }
    }
    BackupFileActions(
        exportPayload = exportPayload,
        exportRequest = exportRequest,
        importRequest = importRequest,
        onImported = { raw ->
            BackupRepository.parse(raw)
                .onSuccess { awaitingRestore = it }
                .onFailure { NuvioToastController.show("This file is not a supported Anivio backup.") }
        },
        onError = { NuvioToastController.show(it) },
    )
    awaitingRestore?.let { backup ->
        NuvioStatusModal(
            title = "Restore profile backup?",
            message = "This replaces the library, history, progress, and collections included in the file for the active profile.",
            isVisible = true,
            confirmText = "Restore",
            dismissText = "Cancel",
            onConfirm = {
                runCatching { BackupRepository.restore(profileId, backup) }
                    .onSuccess { NuvioToastController.show("Backup restored.") }
                    .onFailure { NuvioToastController.show(it.message ?: "Backup could not be restored.") }
                awaitingRestore = null
            },
            onDismiss = { awaitingRestore = null },
        )
    }
}
