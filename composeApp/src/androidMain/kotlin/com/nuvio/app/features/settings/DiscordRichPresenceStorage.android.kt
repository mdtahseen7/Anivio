package com.nuvio.app.features.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal actual object DiscordRichPresencePlatform {
    actual val isSupported: Boolean = false
    actual val isConfigured: Boolean = false
    actual val connected: StateFlow<Boolean> = MutableStateFlow(false)
}

internal actual object DiscordRichPresenceStorage {
    actual fun loadEnabled(): Boolean? = null
    actual fun saveEnabled(enabled: Boolean) = Unit
}
