package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
internal actual fun BackupFileActions(
    exportPayload: String,
    exportRequest: Int,
    importRequest: Int,
    onImported: (String) -> Unit,
    onError: (String) -> Unit,
) {
    var lastExport by remember { mutableIntStateOf(0) }
    var lastImport by remember { mutableIntStateOf(0) }
    LaunchedEffect(exportRequest) {
        if (exportRequest <= lastExport) return@LaunchedEffect
        lastExport = exportRequest
        val file = chooseBackupFile(save = true) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { file.writeText(exportPayload) } }
            .onFailure { onError(it.message ?: "Could not save the backup.") }
    }
    LaunchedEffect(importRequest) {
        if (importRequest <= lastImport) return@LaunchedEffect
        lastImport = importRequest
        val file = chooseBackupFile(save = false) ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { file.readText() } }
            .onSuccess(onImported)
            .onFailure { onError(it.message ?: "Could not open the backup.") }
    }
}

private suspend fun chooseBackupFile(save: Boolean): File? = withContext(Dispatchers.Swing) {
    val chooser = JFileChooser().apply {
        fileFilter = FileNameExtensionFilter("Anivio backup", "json")
        if (save) selectedFile = File("anivio-backup.json")
    }
    val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return@withContext null
    val selected = chooser.selectedFile
    if (!save) return@withContext selected
    val file = if (selected.extension.equals("json", true)) selected else File(selected.path + ".json")
    if (
        file.exists() && JOptionPane.showConfirmDialog(
            null, "Replace ${file.name}?", "Save backup", JOptionPane.YES_NO_OPTION,
        ) != JOptionPane.YES_OPTION
    ) return@withContext null
    file
}
