package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

/** Platform document picker bridge. Clipboard remains available as a portable fallback. */
@Composable
internal expect fun BackupFileActions(
    exportPayload: String,
    exportRequest: Int,
    importRequest: Int,
    onImported: (String) -> Unit,
    onError: (String) -> Unit,
)
