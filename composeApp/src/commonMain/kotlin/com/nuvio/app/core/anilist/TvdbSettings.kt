package com.nuvio.app.core.anilist

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TvdbSettings(
    /** User-supplied key. Blank falls back to the key baked in at build time, if any. */
    val apiKey: String = "",
) {
    val hasUserApiKey: Boolean get() = apiKey.isNotBlank()
}

internal expect object TvdbSettingsStorage {
    fun loadApiKey(): String?
    fun saveApiKey(apiKey: String)
}

/**
 * Holds the user's own TheTVDB key.
 *
 * [TvdbConfig] carries a key baked in from `local.properties` at build time; a key entered here
 * takes precedence, so anyone building from source without one can supply their own.
 */
object TvdbSettingsRepository {
    private val loaded = atomic(false)
    private val _uiState = MutableStateFlow(TvdbSettings())
    val uiState: StateFlow<TvdbSettings> = _uiState.asStateFlow()

    fun ensureLoaded() {
        if (loaded.value) return
        _uiState.value = TvdbSettings(apiKey = TvdbSettingsStorage.loadApiKey().orEmpty().trim())
        loaded.value = true
    }

    fun snapshot(): TvdbSettings {
        ensureLoaded()
        return _uiState.value
    }

    fun setApiKey(apiKey: String) {
        ensureLoaded()
        val normalized = apiKey.trim()
        TvdbSettingsStorage.saveApiKey(normalized)
        _uiState.value = _uiState.value.copy(apiKey = normalized)
        // A different key means a different session; drop the cached bearer.
        TvdbClient.onApiKeyChanged()
    }

    /** The key TVDB requests should use, user-entered first. */
    fun effectiveApiKey(): String =
        snapshot().apiKey.ifBlank { TvdbConfig.API_KEY }.trim()

    val isConfigured: Boolean
        get() = effectiveApiKey().isNotBlank()
}
