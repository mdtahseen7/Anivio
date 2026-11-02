package com.nuvio.app.features.settings

import com.nuvio.app.features.details.MangaReaderProgressRepository
import com.nuvio.app.features.watched.WatchedRepository
import com.nuvio.app.features.watchprogress.WatchProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-wide privacy mode. Playback remains usable, but progress/history writes are skipped. */
object IncognitoModeRepository {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        _enabled.value = ThemeSettingsStorage.loadIncognitoModeEnabled() ?: false
    }

    fun setEnabled(value: Boolean) {
        ensureLoaded()
        if (_enabled.value == value) return
        _enabled.value = value
        ThemeSettingsStorage.saveIncognitoModeEnabled(value)
        WatchProgressRepository.clearLocalState()
        WatchedRepository.clearLocalState()
        MangaReaderProgressRepository.clearLocalState()
    }

    fun clearLocalState() {
        hasLoaded = false
        _enabled.value = false
    }
}
