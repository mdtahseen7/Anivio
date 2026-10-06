package com.nuvio.app.features.cloudflare

/** Platform persistence for the Cloudflare clearance setting + captured per-host tokens. */
internal expect object CfClearanceStorage {
    fun loadEnabled(): Boolean?
    fun saveEnabled(enabled: Boolean)

    /** JSON-encoded Map<host, CfClearanceEntry>, or null if nothing stored yet. */
    fun loadClearancesJson(): String?
    fun saveClearancesJson(json: String)
}
