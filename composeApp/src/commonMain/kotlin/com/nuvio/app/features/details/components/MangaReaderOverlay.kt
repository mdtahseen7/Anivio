package com.nuvio.app.features.details.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nuvio.app.features.plugins.MangaPluginChapter
import com.nuvio.app.features.plugins.PluginScraper

/**
 * Screen-level manga reader state, shared by the Chapters section taps and the
 * details Read button. Rendered once, fullscreen, at the details screen root —
 * never inline inside a lazy item and never in a Dialog window, both of which
 * defeat fullscreen and break touch handling.
 */
data class MangaReaderRequest(
    val title: String,
    val metaId: String,
    val provider: PluginScraper?,
    val providerId: String,
    val chapters: List<MangaPluginChapter>,
    val chapter: MangaPluginChapter,
    val localPageUris: List<String>? = null,
)

object MangaReaderOverlay {
    var request by mutableStateOf<MangaReaderRequest?>(null)
        private set

    fun open(
        title: String,
        metaId: String,
        provider: PluginScraper,
        chapters: List<MangaPluginChapter>,
        chapter: MangaPluginChapter,
    ) {
        if (chapters.isEmpty()) return
        request = MangaReaderRequest(
            title = title,
            metaId = metaId,
            provider = provider,
            providerId = provider.id,
            chapters = chapters,
            chapter = chapter,
        )
    }

    fun openLocal(
        title: String,
        metaId: String,
        providerId: String,
        chapter: MangaPluginChapter,
        localPageUris: List<String>,
    ) {
        if (localPageUris.isEmpty()) return
        request = MangaReaderRequest(
            title = title,
            metaId = metaId,
            provider = null,
            providerId = providerId,
            chapters = listOf(chapter),
            chapter = chapter,
            localPageUris = localPageUris,
        )
    }

    fun selectChapter(chapter: MangaPluginChapter) {
        request?.let { request = it.copy(chapter = chapter) }
    }

    fun close() {
        request = null
    }
}
