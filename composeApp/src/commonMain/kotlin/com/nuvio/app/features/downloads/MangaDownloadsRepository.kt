package com.nuvio.app.features.downloads

import com.nuvio.app.features.plugins.MangaPluginChapter
import com.nuvio.app.features.plugins.MangaPluginPage
import com.nuvio.app.features.plugins.PluginRepository
import com.nuvio.app.features.plugins.PluginScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Owns persisted manga chapter downloads independently from video downloads. */
object MangaDownloadsRepository {
    private val _uiState = MutableStateFlow(MangaDownloadsUiState())
    val uiState: StateFlow<MangaDownloadsUiState> = _uiState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = mutableMapOf<String, Job>()
    private var hasLoaded = false
    private var nextOrdinal = 0L

    fun ensureLoaded() {
        if (!hasLoaded) loadFromDisk()
    }

    fun onProfileChanged() {
        activeJobs.values.forEach(Job::cancel)
        activeJobs.clear()
        loadFromDisk()
    }

    fun clearLocalState() {
        activeJobs.values.forEach(Job::cancel)
        activeJobs.clear()
        hasLoaded = false
        _uiState.value = MangaDownloadsUiState()
    }

    fun findDownload(
        parentMetaId: String,
        providerId: String,
        chapterId: String,
    ): MangaDownloadItem? {
        ensureLoaded()
        val key = buildLogicalKey(parentMetaId, providerId, chapterId)
        return _uiState.value.items.firstOrNull { it.logicalContentKey == key }
    }

    /** Queues a chapter and downloads its pages sequentially in original page order. */
    fun enqueueChapter(
        parentMetaId: String,
        title: String,
        poster: String?,
        provider: PluginScraper,
        chapter: MangaPluginChapter,
    ) {
        ensureLoaded()
        val now = DownloadsClock.nowEpochMs()
        val logicalKey = buildLogicalKey(parentMetaId, provider.id, chapter.id)
        _uiState.value.items.firstOrNull { it.logicalContentKey == logicalKey }?.let { existing ->
            activeJobs.remove(existing.id)?.cancel()
            removeLocalPages(existing)
        }

        val item = MangaDownloadItem(
            id = nextDownloadId(now),
            parentMetaId = parentMetaId,
            title = title,
            poster = poster,
            providerId = provider.id,
            providerName = provider.name,
            chapterId = chapter.id,
            chapterNumber = chapter.number,
            chapterTitle = chapter.title,
            status = DownloadStatus.Downloading,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
        )
        publish(_uiState.value.items.filterNot { it.logicalContentKey == logicalKey }.let { listOf(item) + it })
        persist()
        startDownload(item, provider)
    }

    fun pauseDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return
        if (item.status != DownloadStatus.Downloading) return
        activeJobs.remove(downloadId)?.cancel()
        mutateItem(downloadId) { current ->
            current.copy(status = DownloadStatus.Paused, updatedAtEpochMs = DownloadsClock.nowEpochMs())
        }
    }

    fun resumeDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return
        if (item.status != DownloadStatus.Paused && item.status != DownloadStatus.Failed) return
        val provider = findProvider(item.providerId)
        if (provider == null) {
            mutateItem(downloadId) { current ->
                current.copy(
                    status = DownloadStatus.Failed,
                    errorMessage = "The manga provider is no longer enabled.",
                    updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                )
            }
            return
        }
        val resumed = item.copy(
            status = DownloadStatus.Downloading,
            errorMessage = null,
            updatedAtEpochMs = DownloadsClock.nowEpochMs(),
        )
        replaceItem(resumed)
        persist()
        startDownload(resumed, provider)
    }

    fun retryDownload(downloadId: String) = resumeDownload(downloadId)

    fun cancelDownload(downloadId: String) {
        ensureLoaded()
        val item = _uiState.value.items.firstOrNull { it.id == downloadId } ?: return
        activeJobs.remove(downloadId)?.cancel()
        removeLocalPages(item)
        publish(_uiState.value.items.filterNot { it.id == downloadId })
        persist()
    }

    fun localPageUris(item: MangaDownloadItem): List<String> {
        ensureLoaded()
        return item.pages
            .sortedBy(MangaDownloadPage::pageIndex)
            .mapNotNull { page ->
                DownloadsPlatformDownloader.resolveLocalFileUri(page.localFileUri, page.fileName)
            }
    }

    private fun startDownload(item: MangaDownloadItem, provider: PluginScraper) {
        val job = scope.launch {
            try {
                val remotePages = PluginRepository.getMangaChapterPages(provider, item.chapterId)
                    .getOrElse { error(it.message ?: "Unable to load manga pages.") }
                if (remotePages.isEmpty()) error("This chapter has no pages.")

                mutateItem(item.id) { current ->
                    current.copy(
                        totalPages = remotePages.size,
                        updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                    )
                }

                var completedPages = _uiState.value.items
                    .firstOrNull { it.id == item.id }
                    ?.pages
                    .orEmpty()
                    .toMutableList()

                remotePages.forEachIndexed { index, remotePage ->
                    ensureActive()
                    val existing = completedPages.firstOrNull { it.pageIndex == index }
                    if (existing != null && DownloadsPlatformDownloader.resolveLocalFileUri(existing.localFileUri, existing.fileName) != null) {
                        return@forEachIndexed
                    }

                    val fileName = buildPageFileName(item, index, remotePage.url)
                    val bytes = httpDownloadBytes(
                        url = remotePage.url,
                        headers = remotePage.headers.withDefaultDownloadHeaders(),
                    )
                    ensureActive()
                    val localFileUri = DownloadsPlatformDownloader.saveAuxiliaryFile(fileName, bytes)
                        ?: error("Unable to save manga page ${index + 1}.")
                    completedPages.removeAll { it.pageIndex == index }
                    completedPages += MangaDownloadPage(index, fileName, localFileUri)
                    completedPages.sortBy(MangaDownloadPage::pageIndex)
                    mutateItem(item.id) { current ->
                        current.copy(
                            pages = completedPages.toList(),
                            updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                        )
                    }
                }

                mutateItem(item.id) { current ->
                    current.copy(
                        status = DownloadStatus.Completed,
                        pages = completedPages.toList(),
                        errorMessage = null,
                        updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                    )
                }
            } catch (cancelled: CancellationException) {
                mutateItem(item.id) { current ->
                    if (current.status == DownloadStatus.Downloading) {
                        current.copy(status = DownloadStatus.Paused, updatedAtEpochMs = DownloadsClock.nowEpochMs())
                    } else {
                        current
                    }
                }
                throw cancelled
            } catch (error: Throwable) {
                mutateItem(item.id) { current ->
                    current.copy(
                        status = DownloadStatus.Failed,
                        errorMessage = error.message ?: "Manga download failed.",
                        updatedAtEpochMs = DownloadsClock.nowEpochMs(),
                    )
                }
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    private fun findProvider(providerId: String): PluginScraper? {
        PluginRepository.initialize()
        return PluginRepository.uiState.value.scrapers.firstOrNull { it.id == providerId && it.enabled }
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val payload = DownloadsStorage.loadMangaPayload().orEmpty().trim()
        val loaded = if (payload.isBlank()) emptyList() else MangaDownloadsCodec.decodeItems(payload)
        val normalized = loaded.map { item ->
            if (item.status == DownloadStatus.Downloading) {
                item.copy(status = DownloadStatus.Paused, errorMessage = null)
            } else {
                item
            }
        }
        _uiState.value = MangaDownloadsUiState(normalized)
        if (normalized != loaded) persist()
    }

    private fun mutateItem(id: String, transform: (MangaDownloadItem) -> MangaDownloadItem) {
        var changed = false
        val updated = _uiState.value.items.map { item ->
            if (item.id == id) {
                changed = true
                transform(item)
            } else {
                item
            }
        }
        if (changed) {
            publish(updated)
            persist()
        }
    }

    private fun replaceItem(item: MangaDownloadItem) {
        publish(_uiState.value.items.map { current -> if (current.id == item.id) item else current })
    }

    private fun publish(items: List<MangaDownloadItem>) {
        _uiState.update { MangaDownloadsUiState(items) }
    }

    private fun persist() {
        DownloadsStorage.saveMangaPayload(MangaDownloadsCodec.encodeItems(_uiState.value.items))
    }

    private fun removeLocalPages(item: MangaDownloadItem) {
        item.pages.forEach { page ->
            DownloadsPlatformDownloader.removeFile(
                DownloadsPlatformDownloader.resolveLocalFileUri(page.localFileUri, page.fileName)
                    ?: page.localFileUri,
            )
        }
    }

    private fun nextDownloadId(now: Long): String {
        nextOrdinal += 1L
        return "manga_${now.toString(36)}_${nextOrdinal.toString(36)}"
    }
}

@Serializable
private data class StoredMangaDownloadsPayload(
    val items: List<MangaDownloadItem> = emptyList(),
)

private object MangaDownloadsCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun decodeItems(payload: String): List<MangaDownloadItem> =
        runCatching { json.decodeFromString<StoredMangaDownloadsPayload>(payload).items }
            .getOrDefault(emptyList())

    fun encodeItems(items: Collection<MangaDownloadItem>): String =
        json.encodeToString(StoredMangaDownloadsPayload(items.toList()))
}

private fun buildLogicalKey(parentMetaId: String, providerId: String, chapterId: String): String =
    "${parentMetaId.trim()}|${providerId.trim()}|${chapterId.trim()}"

private fun buildPageFileName(item: MangaDownloadItem, pageIndex: Int, sourceUrl: String): String {
    val extension = sourceUrl.substringBefore('?').substringBefore('#').substringAfterLast('.', "jpg")
        .lowercase()
        .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        ?: "jpg"
    val base = "${item.title} Ch${item.chapterNumber} ${pageIndex + 1}"
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .trim()
        .take(80)
        .ifBlank { "manga" }
    return "${base}_${item.id}.$extension"
}
