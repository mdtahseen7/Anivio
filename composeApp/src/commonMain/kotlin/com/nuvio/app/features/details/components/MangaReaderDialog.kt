package com.nuvio.app.features.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.nuvio.app.features.plugins.MangaPluginChapter
import com.nuvio.app.features.plugins.MangaPluginPage

@Composable
internal fun MangaReaderDialog(
    title: String,
    sourceName: String,
    chapter: MangaPluginChapter,
    chapters: List<MangaPluginChapter>,
    pages: List<MangaPluginPage>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelectChapter: (MangaPluginChapter) -> Unit,
    onChapterCompleted: (MangaPluginChapter) -> Unit,
) {
    val listState = rememberLazyListState()
    var controlsVisible by remember { mutableStateOf(true) }
    var chapterMenuOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val currentPage = (listState.firstVisibleItemIndex + 1).coerceAtMost(pages.size.coerceAtLeast(1))
    val progress = if (pages.isEmpty()) 0f else currentPage.toFloat() / pages.size
    val chapterIndex = chapters.indexOfFirst { it.id == chapter.id }
    val previous = chapters.getOrNull(chapterIndex - 1)
    val next = chapters.getOrNull(chapterIndex + 1)
    var completionReported by remember(chapter.id) { mutableStateOf(false) }

    LaunchedEffect(currentPage, pages.size, chapter.id) {
        if (!completionReported && pages.isNotEmpty() && currentPage == pages.size) {
            completionReported = true
            onChapterCompleted(chapter)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0D0E12),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading -> ReaderStatus("Loading chapter pages…")
                    pages.isEmpty() -> ReaderStatus("This provider returned no pages for this chapter.")
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { controlsVisible = !controlsVisible },
                        state = listState,
                    ) {
                        itemsIndexed(pages, key = { index, page -> "${page.url}-$index" }) { index, page ->
                            AsyncImage(
                                model = page.url,
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        title = title,
                        chapter = chapter,
                        sourceName = sourceName,
                        chapterMenuOpen = chapterMenuOpen,
                        chapters = chapters,
                        onDismiss = onDismiss,
                        onToggleChapters = { chapterMenuOpen = !chapterMenuOpen },
                        onSelectChapter = { chapterMenuOpen = false; onSelectChapter(it) },
                        onSettings = { settingsOpen = true },
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible && pages.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    ReaderBottomBar(
                        currentPage = currentPage,
                        totalPages = pages.size,
                        progress = progress,
                        previous = previous,
                        next = next,
                        onPrevious = { previous?.let(onSelectChapter) },
                        onNext = { next?.let(onSelectChapter) },
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        ModalBottomSheet(onDismissRequest = { settingsOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Reader settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Long strip", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Pages are shown in a continuous vertical flow. Tap the reader to hide or reveal controls.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ReaderStatus(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapter: MangaPluginChapter,
    sourceName: String,
    chapterMenuOpen: Boolean,
    chapters: List<MangaPluginChapter>,
    onDismiss: () -> Unit,
    onToggleChapters: () -> Unit,
    onSelectChapter: (MangaPluginChapter) -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = Color(0xE60D0E12), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close reader", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Box {
                    Text(
                        text = "Chapter ${chapter.number}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onToggleChapters),
                    )
                    DropdownMenu(expanded = chapterMenuOpen, onDismissRequest = onToggleChapters) {
                        chapters.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("Chapter ${option.number}") },
                                onClick = { onSelectChapter(option) },
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            AssistChip(onClick = {}, label = { Text(sourceName) }, modifier = Modifier.padding(end = 4.dp))
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.MoreVert, "Reader settings", tint = Color.White)
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentPage: Int,
    totalPages: Int,
    progress: Float,
    previous: MangaPluginChapter?,
    next: MangaPluginChapter?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(color = Color(0xE60D0E12), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Page $currentPage of $totalPages", color = Color.White, style = MaterialTheme.typography.labelMedium)
                Text("  •  Tap to hide controls", color = Color.White.copy(alpha = 0.56f), style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.18f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious, enabled = previous != null) {
                    Icon(Icons.Rounded.KeyboardArrowLeft, "Previous chapter", tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Text(
                    text = listOfNotNull(previous?.let { "← ${it.number}" }, next?.let { "${it.number} →" }).joinToString("     "),
                    color = Color.White.copy(alpha = 0.76f),
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = onNext, enabled = next != null) {
                    Icon(Icons.Rounded.KeyboardArrowRight, "Next chapter", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
