package com.nuvio.app.features.cloudflare

import platform.Foundation.NSUserDefaults

actual object CfClearanceStorage {
    private const val enabledKey = "nuvio_cf_clearance_enabled"
    private const val clearancesKey = "nuvio_cf_clearances_json"
    private val defaults get() = NSUserDefaults.standardUserDefaults

    actual fun loadEnabled(): Boolean? =
        if (defaults.objectForKey(enabledKey) != null) defaults.boolForKey(enabledKey) else null

    actual fun saveEnabled(enabled: Boolean) {
        defaults.setBool(enabled, enabledKey)
    }

    actual fun loadClearancesJson(): String? =
        defaults.stringForKey(clearancesKey)

    actual fun saveClearancesJson(json: String) {
        defaults.setObject(json, clearancesKey)
    }
}
