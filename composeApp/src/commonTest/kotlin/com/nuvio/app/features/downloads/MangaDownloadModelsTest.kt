package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class MangaDownloadModelsTest {
    @Test
    fun `chapter key separates manga provider and chapter`() {
        val item = MangaDownloadItem(
            id = "manga-1",
            parentMetaId = "  manga-42 ",
            title = "Example",
            providerId = "provider-a",
            providerName = "Provider A",
            chapterId = "chapter-7",
            chapterNumber = "7",
            status = DownloadStatus.Downloading,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        assertEquals("manga-42|provider-a|chapter-7", item.logicalContentKey)
    }

    @Test
    fun `chapter progress is based on downloaded pages`() {
        val item = MangaDownloadItem(
            id = "manga-1",
            parentMetaId = "manga-42",
            title = "Example",
            providerId = "provider-a",
            providerName = "Provider A",
            chapterId = "chapter-7",
            chapterNumber = "7",
            pages = listOf(
                MangaDownloadPage(0, "page-1.jpg", "file:///page-1.jpg"),
                MangaDownloadPage(1, "page-2.jpg", "file:///page-2.jpg"),
            ),
            status = DownloadStatus.Downloading,
            totalPages = 5,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        assertEquals(2, item.downloadedPages)
        assertEquals(0.4f, item.progressFraction)
    }
}
