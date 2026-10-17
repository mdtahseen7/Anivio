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
    // Desktop keeps the clipboard fallback for now.
}
