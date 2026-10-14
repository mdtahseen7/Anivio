package com.nuvio.app.features.details.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.details.MetaDetails
import com.nuvio.app.features.anilist.parseAniListMediaId
import com.nuvio.app.features.anilist.recordAniListMangaChapterRead
import com.nuvio.app.features.plugins.MangaPluginChapter
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.launch

private const val CHAPTER_PAGE_SIZE = 10

/** Manga-only: providers are explicitly opt-in via `manga: true` in their manifest. */
@Composable
internal fun MangaChaptersSection(meta: MetaDetails) {
    PluginRepository.initialize()
    val plugins by PluginRepository.uiState.collectAsStateWithLifecycle()
    val providers = remember(plugins.scrapers) { PluginRepository.getEnabledMangaScrapers() }
    var selectedProvider by remember(meta.id, providers) { mutableStateOf(providers.firstOrNull()) }
    var chapters by remember(meta.id, selectedProvider?.id) { mutableStateOf<List<MangaPluginChapter>>(emptyList()) }
    var loading by remember(meta.id, selectedProvider?.id) { mutableStateOf(false) }
    var visibleCount by remember(meta.id, selectedProvider?.id) { mutableIntStateOf(CHAPTER_PAGE_SIZE) }
    var menuOpen by remember { mutableStateOf(false) }
    var readingChapter by remember { mutableStateOf<MangaPluginChapter?>(null) }
    var pages by remember(readingChapter?.id, selectedProvider?.id) { mutableStateOf(emptyList<com.nuvio.app.features.plugins.MangaPluginPage>()) }
    var loadingPages by remember(readingChapter?.id, selectedProvider?.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load(provider: PluginScraper?) {
        if (provider == null) return
        scope.launch {
            loading = true
            chapters = PluginRepository.getMangaChapters(
                scraper = provider,
                mangaId = parseAniListMediaId(meta.id)?.toString().orEmpty(),
                title = meta.name,
            ).getOrDefault(emptyList())
            visibleCount = CHAPTER_PAGE_SIZE
            loading = false
        }
    }
    LaunchedEffect(selectedProvider?.id, meta.id) { load(selectedProvider) }
    LaunchedEffect(readingChapter?.id, selectedProvider?.id) {
        val chapter = readingChapter ?: return@LaunchedEffect
        val provider = selectedProvider ?: return@LaunchedEffect
        loadingPages = true
        pages = PluginRepository.getMangaChapterPages(provider, chapter.id).getOrDefault(emptyList())
        loadingPages = false
    }

    DetailSection(title = "Chapters", showHeader = true) {
        if (providers.isEmpty()) {
            Text("Install and enable a manga provider to load chapters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@DetailSection
        }
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            Button(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(50),
            ) { Text(selectedProvider?.name ?: "Choose server") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                providers.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.name) },
                        onClick = { selectedProvider = provider; menuOpen = false },
                    )
                }
            }
        }
        if (loading) Text("Loading chapters…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        chapters.take(visibleCount).forEach { chapter ->
            Text(
                text = buildString { append("Chapter "); append(chapter.number); chapter.title?.let { append(" · "); append(it) } },
                modifier = Modifier.fillMaxWidth().clickable { readingChapter = chapter }.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        if (chapters.size > visibleCount) {
            Button(onClick = { visibleCount += CHAPTER_PAGE_SIZE }) { Text("Load more chapters") }
        }
    }

    readingChapter?.let { chapter ->
        MangaReaderDialog(
            title = meta.name,
            sourceName = selectedProvider?.name.orEmpty(),
            chapter = chapter,
            chapters = chapters,
            pages = pages,
            loading = loadingPages,
            onDismiss = { readingChapter = null },
            onSelectChapter = { readingChapter = it },
            onChapterCompleted = { completedChapter ->
                scope.launch {
                    recordAniListMangaChapterRead(meta.id, completedChapter.number)
                }
            },
        )
    }
}
