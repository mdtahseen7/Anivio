package com.nuvio.app.features.downloads

import kotlinx.serialization.Serializable

@Serializable
data class MangaDownloadPage(
    val pageIndex: Int,
    val fileName: String,
    val localFileUri: String,
)

@Serializable
data class MangaDownloadItem(
    val id: String,
    val parentMetaId: String,
    val title: String,
    val poster: String? = null,
    val providerId: String,
    val providerName: String,
    val chapterId: String,
    val chapterNumber: String,
    val chapterTitle: String? = null,
    val pages: List<MangaDownloadPage> = emptyList(),
    val status: DownloadStatus,
    val totalPages: Int = 0,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    val downloadedPages: Int
        get() = pages.size

    val progressFraction: Float
        get() = if (totalPages <= 0) 0f else (downloadedPages.toFloat() / totalPages).coerceIn(0f, 1f)

    val isPlayable: Boolean
        get() = status == DownloadStatus.Completed && pages.isNotEmpty()

    val logicalContentKey: String
        get() = "${parentMetaId.trim()}|${providerId.trim()}|${chapterId.trim()}"
}

data class MangaDownloadsUiState(
    val items: List<MangaDownloadItem> = emptyList(),
) {
    val activeItems: List<MangaDownloadItem>
        get() = items.filter { it.status != DownloadStatus.Completed }

    val completedItems: List<MangaDownloadItem>
        get() = items.filter { it.status == DownloadStatus.Completed }
}
