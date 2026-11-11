package com.nuvio.app.features.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.ChapterPicker(chapters: List<PlayerChapter>, onSeek: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { open = true }, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)) { Text("Chapters") }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Chapters") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            chapters.forEachIndexed { index, chapter ->
                TextButton(onClick = { onSeek(chapter.startMs); open = false }) {
                    Text("${chapter.startMs / 60000}:${((chapter.startMs / 1000) % 60).toString().padStart(2, '0')}  ${chapter.title ?: "Chapter ${index + 1}"}")
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}
