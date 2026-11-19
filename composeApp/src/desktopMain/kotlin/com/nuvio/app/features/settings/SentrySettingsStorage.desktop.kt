package com.nuvio.app.features.settings

import com.nuvio.app.core.diagnostics.SentryConfig
import com.nuvio.app.core.storage.DesktopStorage

internal actual object SentrySettingsPlatform {
    actual val crashReportsSupported: Boolean = SentryConfig.DESKTOP_DSN.isNotBlank()
    actual val usesDesktopCopy: Boolean = true
}

internal actual object SentrySettingsStorage {
    private const val enabledKey = "enabled"
    private val store = DesktopStorage.store("nuvio_sentry_settings")

    actual fun loadEnabled(): Boolean? =
        if (store.contains(enabledKey)) store.getBoolean(enabledKey) else null

    actual fun saveEnabled(enabled: Boolean) {
        store.putBoolean(enabledKey, enabled)
    }

    actual fun loadPayload(): String? =
        if (store.contains(enabledKey)) (store.getBoolean(enabledKey) ?: true).toString() else null

    actual fun savePayload(payload: String) {
        store.putBoolean(enabledKey, payload.trim().equals("true", ignoreCase = true))
    }
}
