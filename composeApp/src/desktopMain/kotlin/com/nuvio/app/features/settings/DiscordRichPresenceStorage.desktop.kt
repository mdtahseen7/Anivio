package com.nuvio.app.features.settings

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.features.discordrpc.DiscordConfig
import com.nuvio.app.features.discordrpc.DiscordPresenceManager
import kotlinx.coroutines.flow.StateFlow

internal actual object DiscordRichPresencePlatform {
    actual val isSupported: Boolean = true
    actual val isConfigured: Boolean get() = DiscordConfig.CLIENT_ID.isNotBlank()
    actual val connected: StateFlow<Boolean> get() = DiscordPresenceManager.connected
}

internal actual object DiscordRichPresenceStorage {
    private const val enabledKey = "enabled"
    private val store = DesktopStorage.store("nuvio_discord_rich_presence")

    actual fun loadEnabled(): Boolean? =
        if (store.contains(enabledKey)) store.getBoolean(enabledKey) else null

    actual fun saveEnabled(enabled: Boolean) {
        store.putBoolean(enabledKey, enabled)
    }
}
