package com.nuvio.app.features.downloads

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadsStorageSummaryTest {
    @Test
    fun `summary counts queued paused active failed and stored bytes`() {
        val video = DownloadItem(
            id = "video-1",
            contentType = "series",
            parentMetaId = "show-1",
            parentMetaType = "series",
            videoId = "episode-1",
            title = "Example",
            streamTitle = "1080p",
            providerName = "Provider",
            sourceUrl = "https://example.test/video.mp4",
            fileName = "video.mp4",
            status = DownloadStatus.Completed,
            downloadedBytes = 120L,
            totalBytes = 100L,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val queued = video.copy(id = "video-2", status = DownloadStatus.Queued, totalBytes = null, downloadedBytes = 7L)
        val paused = video.copy(id = "video-3", status = DownloadStatus.Paused)
        val failed = video.copy(id = "video-4", status = DownloadStatus.Failed)

        val summary = DownloadsUiState(listOf(video, queued, paused, failed)).storageSummary()

        assertEquals(1, summary.queuedCount)
        assertEquals(0, summary.activeCount)
        assertEquals(1, summary.pausedCount)
        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.failedCount)
        assertEquals(307L, summary.bytes)
    }

    @Test
    fun `manga summary uses persisted page sizes`() {
        val item = MangaDownloadItem(
            id = "manga-1",
            parentMetaId = "manga",
            title = "Example",
            providerId = "provider",
            providerName = "Provider",
            chapterId = "chapter",
            chapterNumber = "1",
            pages = listOf(
                MangaDownloadPage(0, "one.jpg", "file:///one.jpg", sizeBytes = 12L),
                MangaDownloadPage(1, "two.jpg", "file:///two.jpg", sizeBytes = 30L),
            ),
            status = DownloadStatus.Completed,
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )

        assertEquals(42L, MangaDownloadsUiState(listOf(item)).storageSummary().bytes)
    }
}
