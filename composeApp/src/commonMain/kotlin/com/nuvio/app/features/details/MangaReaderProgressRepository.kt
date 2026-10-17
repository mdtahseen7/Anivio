package com.nuvio.app.features.details

import com.nuvio.app.features.downloads.DownloadsClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Serializable
data class MangaReaderProgressEntry(
    val key: String,
    val pageIndex: Int = 0,
    val webtoonOffset: Int = 0,
    val completed: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

/** A saved location inside a chapter. Bookmarks intentionally share the reader's profile scope. */
@Serializable
data class MangaReaderBookmark(
    val key: String,
    val pageIndex: Int,
    val webtoonOffset: Int = 0,
    val createdAtEpochMs: Long = 0L,
)

/** Pure bookmark toggle used by the repository and common tests. */
fun toggleMangaReaderBookmark(
    bookmarks: List<MangaReaderBookmark>,
    key: String,
    pageIndex: Int,
    webtoonOffset: Int = 0,
    createdAtEpochMs: Long = 0L,
): List<MangaReaderBookmark> {
    if (key.isBlank()) return bookmarks
    val normalizedPage = pageIndex.coerceAtLeast(0)
    val normalizedOffset = webtoonOffset.coerceAtLeast(0)
    val existing = bookmarks.indexOfFirst {
        it.key == key && it.pageIndex == normalizedPage && it.webtoonOffset == normalizedOffset
    }
    return if (existing >= 0) {
        bookmarks.toMutableList().also { it.removeAt(existing) }
    } else {
        bookmarks + MangaReaderBookmark(
            key = key,
            pageIndex = normalizedPage,
            webtoonOffset = normalizedOffset,
            createdAtEpochMs = createdAtEpochMs,
        )
    }
}

fun mangaReaderProgressKey(
    metaId: String,
    providerId: String,
    chapterId: String,
): String = "${metaId.trim()}|${providerId.trim()}|${chapterId.trim()}"

object MangaReaderProgressRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var hasLoaded = false
    private val entries = linkedMapOf<String, MangaReaderProgressEntry>()
    private val bookmarks = linkedMapOf<String, MutableList<MangaReaderBookmark>>()
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        entries.clear()
        bookmarks.clear()
        val payload = MangaReaderProgressStorage.loadPayload().orEmpty().trim()
        if (payload.isBlank()) return
        val stored = runCatching {
            json.decodeFromString<StoredMangaReaderProgress>(payload)
        }.getOrNull() ?: return
        stored.entries.forEach { entry ->
            if (entry.key.isNotBlank()) entries[entry.key] = entry
        }
        stored.bookmarks.forEach { bookmark ->
            if (bookmark.key.isNotBlank() && bookmark.pageIndex >= 0) {
                bookmarks.getOrPut(bookmark.key) { mutableListOf() }.add(bookmark)
            }
        }
    }

    fun onProfileChanged() {
        hasLoaded = false
        entries.clear()
        bookmarks.clear()
        ensureLoaded()
    }

    fun clearLocalState() {
        hasLoaded = false
        entries.clear()
        bookmarks.clear()
    }

    /** Returns the complete profile-scoped reader payload for backup or account sync. */
    fun exportPayload(): String {
        ensureLoaded()
        return json.encodeToString(
            StoredMangaReaderProgress(
                entries = entries.values.toList(),
                bookmarks = bookmarks.values.flatten(),
            ),
        )
    }

    /** Replaces the local reader payload after validating its serialized shape. */
    fun replacePayload(payload: String): Boolean {
        if (payload.isBlank()) {
            hasLoaded = true
            entries.clear()
            bookmarks.clear()
            persist()
            _changes.value += 1L
            return true
        }
        val stored = runCatching {
            json.decodeFromString<StoredMangaReaderProgress>(payload)
        }.getOrNull() ?: return false
        hasLoaded = true
        entries.clear()
        bookmarks.clear()
        stored.entries.filter { it.key.isNotBlank() }.forEach { entry -> entries[entry.key] = entry }
        stored.bookmarks.filter { it.key.isNotBlank() && it.pageIndex >= 0 }.forEach { bookmark ->
            bookmarks.getOrPut(bookmark.key) { mutableListOf() }.add(bookmark)
        }
        persist()
        _changes.value += 1L
        return true
    }

    fun progressFor(key: String): MangaReaderProgressEntry? {
        ensureLoaded()
        return entries[key]
    }

    fun save(
        key: String,
        pageIndex: Int,
        webtoonOffset: Int = 0,
        completed: Boolean? = null,
    ) {
        ensureLoaded()
        if (key.isBlank()) return
        entries[key] = MangaReaderProgressEntry(
            key = key,
            pageIndex = pageIndex.coerceAtLeast(0),
            webtoonOffset = webtoonOffset.coerceAtLeast(0),
            completed = completed ?: entries[key]?.completed ?: false,
            updatedAtEpochMs = DownloadsClock.nowEpochMs(),
        )
        persist()
        _changes.value += 1L
    }

    fun bookmarksFor(key: String): List<MangaReaderBookmark> {
        ensureLoaded()
        return bookmarks[key].orEmpty().sortedWith(
            compareBy<MangaReaderBookmark> { it.pageIndex }.thenBy { it.webtoonOffset },
        )
    }

    /** Toggles one location and returns true when the location is now bookmarked. */
    fun toggleBookmark(key: String, pageIndex: Int, webtoonOffset: Int = 0): Boolean {
        ensureLoaded()
        if (key.isBlank()) return false
        val normalizedPage = pageIndex.coerceAtLeast(0)
        val normalizedOffset = webtoonOffset.coerceAtLeast(0)
        val chapterBookmarks = bookmarks.getOrPut(key) { mutableListOf() }
        val updated = toggleMangaReaderBookmark(
            bookmarks = chapterBookmarks,
            key = key,
            pageIndex = normalizedPage,
            webtoonOffset = normalizedOffset,
            createdAtEpochMs = DownloadsClock.nowEpochMs(),
        )
        val isAdded = updated.size > chapterBookmarks.size
        chapterBookmarks.clear()
        chapterBookmarks.addAll(updated)
        if (chapterBookmarks.isEmpty()) bookmarks.remove(key)
        persist()
        _changes.value += 1L
        return isAdded
    }

    fun isBookmarked(key: String, pageIndex: Int, webtoonOffset: Int = 0): Boolean {
        ensureLoaded()
        return bookmarks[key].orEmpty().any {
            it.pageIndex == pageIndex.coerceAtLeast(0) &&
                it.webtoonOffset == webtoonOffset.coerceAtLeast(0)
        }
    }

    private fun persist() {
        MangaReaderProgressStorage.savePayload(
            json.encodeToString(
                StoredMangaReaderProgress(
                    entries = entries.values.toList(),
                    bookmarks = bookmarks.values.flatten(),
                ),
            ),
        )
    }
}

@Serializable
private data class StoredMangaReaderProgress(
    val entries: List<MangaReaderProgressEntry> = emptyList(),
    val bookmarks: List<MangaReaderBookmark> = emptyList(),
)
