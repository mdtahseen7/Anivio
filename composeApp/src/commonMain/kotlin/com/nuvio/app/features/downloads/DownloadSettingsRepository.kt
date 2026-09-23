package com.nuvio.app.features.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DownloadSettingsUiState(
    val defaultServerAddonName: String? = null,
)

object DownloadSettingsRepository {
    private val _uiState = MutableStateFlow(DownloadSettingsUiState())
    val uiState: StateFlow<DownloadSettingsUiState> = _uiState

    private var hasLoaded = false
    private var defaultServerAddonName: String? = null

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    private fun loadFromDisk() {
        defaultServerAddonName = DownloadSettingsStorage.loadDefaultServerAddonName()
        hasLoaded = true
        publish()
    }

    fun setDefaultServerAddonName(name: String?) {
        ensureLoaded()
        if (defaultServerAddonName == name) return
        defaultServerAddonName = name
        publish()
        DownloadSettingsStorage.saveDefaultServerAddonName(name)
    }

    private fun publish() {
        _uiState.value = DownloadSettingsUiState(
            defaultServerAddonName = defaultServerAddonName,
        )
    }
}
