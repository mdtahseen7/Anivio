package com.nuvio.app.features.settings

import kotlinx.coroutines.flow.StateFlow

internal expect object DiscordRichPresencePlatform {
    val isSupported: Boolean
    val isConfigured: Boolean
    val connected: StateFlow<Boolean>
}

internal expect object DiscordRichPresenceStorage {
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)
}
