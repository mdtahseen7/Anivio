package com.nuvio.app.features.cloudflare

import com.nuvio.app.core.storage.DesktopStorage

internal actual object CfClearanceStorage {
    private val store = DesktopStorage.store("cloudflare")
    actual fun loadEnabled(): Boolean? = store.getBoolean("enabled")
    actual fun saveEnabled(enabled: Boolean) {
        store.putBoolean("enabled", enabled)
    }
    actual fun loadClearancesJson(): String? = store.getString("clearances")
    actual fun saveClearancesJson(json: String) {
        store.putString("clearances", json)
    }
}
