package com.nuvio.app.features.tracking

import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

internal actual object TrackingSettingsStorage {
    private const val librarySourceModeKey = "tracking_library_source_mode"
    private const val watchProgressSourceKey = "tracking_watch_progress_source"
    private const val continueWatchingDaysCapKey = "tracking_continue_watching_days_cap"
    private val syncKeys = listOf(
        librarySourceModeKey,
        watchProgressSourceKey,
        continueWatchingDaysCapKey,
    )

    actual fun loadLibrarySourceMode(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(librarySourceModeKey))

    actual fun saveLibrarySourceMode(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(
            value,
            forKey = ProfileScopedKey.of(librarySourceModeKey),
        )
    }

    actual fun loadWatchProgressSource(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(watchProgressSourceKey))

    actual fun saveWatchProgressSource(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(
            value,
            forKey = ProfileScopedKey.of(watchProgressSourceKey),
        )
    }

    actual fun loadContinueWatchingDaysCap(): Int? {
        val scopedKey = ProfileScopedKey.of(continueWatchingDaysCapKey)
        val defaults = NSUserDefaults.standardUserDefaults
        // NSUserDefaults reports 0 for a missing integer, which collides with the "all" sentinel.
        if (defaults.objectForKey(scopedKey) == null) return null
        return defaults.integerForKey(scopedKey).toInt()
    }

    actual fun saveContinueWatchingDaysCap(days: Int) {
        NSUserDefaults.standardUserDefaults.setInteger(
            days.toLong(),
            forKey = ProfileScopedKey.of(continueWatchingDaysCapKey),
        )
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadLibrarySourceMode()?.let { put(librarySourceModeKey, encodeSyncString(it)) }
        loadWatchProgressSource()?.let { put(watchProgressSourceKey, encodeSyncString(it)) }
        loadContinueWatchingDaysCap()?.let { put(continueWatchingDaysCapKey, encodeSyncInt(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        val defaults = NSUserDefaults.standardUserDefaults
        syncKeys.forEach { key -> defaults.removeObjectForKey(ProfileScopedKey.of(key)) }

        payload.decodeSyncString(librarySourceModeKey)?.let(::saveLibrarySourceMode)
        payload.decodeSyncString(watchProgressSourceKey)?.let(::saveWatchProgressSource)
        payload.decodeSyncInt(continueWatchingDaysCapKey)?.let(::saveContinueWatchingDaysCap)
    }
}
