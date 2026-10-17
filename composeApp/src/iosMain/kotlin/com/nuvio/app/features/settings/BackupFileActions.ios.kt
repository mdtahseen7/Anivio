package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

@Composable
internal actual fun BackupFileActions(
    exportPayload: String,
    exportRequest: Int,
    importRequest: Int,
    onImported: (String) -> Unit,
    onError: (String) -> Unit,
) {
    // iOS keeps the clipboard fallback until the document-picker bridge is added to iosApp.
}
