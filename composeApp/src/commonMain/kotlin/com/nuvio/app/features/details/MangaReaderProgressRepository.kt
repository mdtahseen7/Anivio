package com.nuvio.app.features.details

import com.nuvio.app.features.downloads.DownloadsClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MangaReaderProgressEntry(
    val key: String,
    val pageIndex: Int = 0,
    val webtoonOffset: Int = 0,
    val completed: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

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

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        entries.clear()
        val payload = MangaReaderProgressStorage.loadPayload().orEmpty().trim()
        if (payload.isBlank()) return
        val stored = runCatching {
            json.decodeFromString<StoredMangaReaderProgress>(payload)
        }.getOrNull() ?: return
        stored.entries.forEach { entry ->
            if (entry.key.isNotBlank()) entries[entry.key] = entry
        }
    }

    fun onProfileChanged() {
        hasLoaded = false
        entries.clear()
        ensureLoaded()
    }

    fun clearLocalState() {
        hasLoaded = false
        entries.clear()
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
    }

    private fun persist() {
        MangaReaderProgressStorage.savePayload(
            json.encodeToString(StoredMangaReaderProgress(entries.values.toList())),
        )
    }
}

@Serializable
private data class StoredMangaReaderProgress(
    val entries: List<MangaReaderProgressEntry> = emptyList(),
)
