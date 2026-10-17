package com.nuvio.app.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun BackupFileActions(
    exportPayload: String,
    exportRequest: Int,
    importRequest: Int,
    onImported: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(exportPayload.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open the selected backup location.")
        }.onFailure { onError(it.message ?: "Unable to export backup.") }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to open the selected backup file.")
        }.onSuccess(onImported).onFailure { onError(it.message ?: "Unable to import backup.") }
    }

    LaunchedEffect(exportRequest) {
        if (exportRequest > 0) exportLauncher.launch("anivio-backup-v1.json")
    }
    LaunchedEffect(importRequest) {
        if (importRequest > 0) importLauncher.launch(arrayOf("application/json", "text/plain"))
    }
}
