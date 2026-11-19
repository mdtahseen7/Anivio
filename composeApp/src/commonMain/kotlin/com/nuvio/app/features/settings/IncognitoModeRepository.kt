package com.nuvio.app.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stops new playback history and Rich Presence publication for the active profile. */
internal object IncognitoModeRepository {
    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()
    private var loaded = false

    fun isEnabled(): Boolean {
        if (!loaded) reload()
        return _enabled.value
    }

    fun setEnabled(enabled: Boolean) {
        if (isEnabled() == enabled) return
        ThemeSettingsStorage.saveIncognitoModeEnabled(enabled)
        _enabled.value = enabled
    }

    fun reload() {
        loaded = true
        _enabled.value = ThemeSettingsStorage.loadIncognitoModeEnabled() ?: false
    }
}
