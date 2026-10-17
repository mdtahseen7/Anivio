package com.nuvio.app.features.details

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Page progression direction inside the manga reader. */
enum class MangaReadingMode {
    /** Continuous vertical scroll, one long strip. */
    WEBTOON,

    /** Horizontal pager, swiped left to right. */
    LEFT_TO_RIGHT,

    /** Horizontal pager, swiped right to left (classic manga direction). */
    RIGHT_TO_LEFT,
}

data class MangaReaderSettingsUiState(
    val readingMode: MangaReadingMode = MangaReadingMode.WEBTOON,
)

@Serializable
private data class StoredMangaReaderSettings(
    val readingMode: MangaReadingMode = MangaReadingMode.WEBTOON,
)

/**
 * Home for manga reader preferences, shared by the reader itself, its in-reader
 * settings sheet and the Reader settings page.
 */
object MangaReaderSettingsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _uiState = MutableStateFlow(MangaReaderSettingsUiState())
    val uiState: StateFlow<MangaReaderSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun setReadingMode(mode: MangaReadingMode) {
        ensureLoaded()
        if (_uiState.value.readingMode == mode) return
        _uiState.value = _uiState.value.copy(readingMode = mode)
        persist()
    }

    fun exportPayload(): String {
        ensureLoaded()
        return json.encodeToString(
            StoredMangaReaderSettings(readingMode = _uiState.value.readingMode),
        )
    }

    fun replacePayload(payload: String): Boolean {
        if (payload.isBlank()) {
            hasLoaded = true
            _uiState.value = MangaReaderSettingsUiState()
            persist()
            return true
        }
        val stored = runCatching {
            json.decodeFromString<StoredMangaReaderSettings>(payload)
        }.getOrNull() ?: return false
        hasLoaded = true
        _uiState.value = MangaReaderSettingsUiState(readingMode = stored.readingMode)
        persist()
        return true
    }

    private fun loadFromDisk() {
        hasLoaded = true

        val payload = MangaReaderSettingsStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) {
            _uiState.value = MangaReaderSettingsUiState()
            return
        }

        val stored = runCatching {
            json.decodeFromString<StoredMangaReaderSettings>(payload)
        }.getOrNull()
        _uiState.value = if (stored != null) {
            MangaReaderSettingsUiState(readingMode = stored.readingMode)
        } else {
            MangaReaderSettingsUiState()
        }
    }

    private fun persist() {
        MangaReaderSettingsStorage.savePayload(
            json.encodeToString(
                StoredMangaReaderSettings(readingMode = _uiState.value.readingMode),
            ),
        )
    }
}
