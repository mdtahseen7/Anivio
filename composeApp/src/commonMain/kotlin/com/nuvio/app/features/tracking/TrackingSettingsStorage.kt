package com.nuvio.app.features.tracking

import kotlinx.serialization.json.JsonObject

internal expect object TrackingSettingsStorage {
    fun loadLibrarySourceMode(): String?
    fun saveLibrarySourceMode(value: String)
    fun loadWatchProgressSource(): String?
    fun saveWatchProgressSource(value: String)
    fun loadContinueWatchingDaysCap(): Int?
    fun saveContinueWatchingDaysCap(days: Int)
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
