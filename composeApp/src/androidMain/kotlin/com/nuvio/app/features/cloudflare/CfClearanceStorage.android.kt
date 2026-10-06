package com.nuvio.app.features.cloudflare

import android.content.Context
import android.content.SharedPreferences

actual object CfClearanceStorage {
    private const val preferencesName = "nuvio_cf_clearance"
    private const val enabledKey = "cf_clearance_enabled"
    private const val clearancesKey = "cf_clearances_json"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadEnabled(): Boolean? {
        val prefs = preferences ?: return null
        return if (prefs.contains(enabledKey)) prefs.getBoolean(enabledKey, false) else null
    }

    actual fun saveEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(enabledKey, enabled)?.apply()
    }

    actual fun loadClearancesJson(): String? = preferences?.getString(clearancesKey, null)

    actual fun saveClearancesJson(json: String) {
        preferences?.edit()?.putString(clearancesKey, json)?.apply()
    }
}
