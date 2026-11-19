package com.nuvio.app.features.tracking

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object TrackingSettingsStorage {
    private const val librarySourceModeKey = "tracking_library_source_mode"
    private const val watchProgressSourceKey = "tracking_watch_progress_source"
    private const val continueWatchingDaysCapKey = "tracking_continue_watching_days_cap"
    private val syncKeys = listOf(
        librarySourceModeKey,
        watchProgressSourceKey,
        continueWatchingDaysCapKey,
    )
    private val store = DesktopStorage.store("anivio_tracking_settings")

    actual fun loadLibrarySourceMode(): String? = loadString(librarySourceModeKey)

    actual fun saveLibrarySourceMode(value: String) = saveString(librarySourceModeKey, value)

    actual fun loadWatchProgressSource(): String? = loadString(watchProgressSourceKey)

    actual fun saveWatchProgressSource(value: String) = saveString(watchProgressSourceKey, value)

    actual fun loadContinueWatchingDaysCap(): Int? {
        val scopedKey = ProfileScopedKey.of(continueWatchingDaysCapKey)
        return if (store.contains(scopedKey)) store.getInt(scopedKey) else null
    }

    actual fun saveContinueWatchingDaysCap(days: Int) {
        store.putInt(ProfileScopedKey.of(continueWatchingDaysCapKey), days)
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadLibrarySourceMode()?.let { put(librarySourceModeKey, encodeSyncString(it)) }
        loadWatchProgressSource()?.let { put(watchProgressSourceKey, encodeSyncString(it)) }
        loadContinueWatchingDaysCap()?.let { put(continueWatchingDaysCapKey, encodeSyncInt(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        store.removeAll(syncKeys.map { ProfileScopedKey.of(it) })

        payload.decodeSyncString(librarySourceModeKey)?.let(::saveLibrarySourceMode)
        payload.decodeSyncString(watchProgressSourceKey)?.let(::saveWatchProgressSource)
        payload.decodeSyncInt(continueWatchingDaysCapKey)?.let(::saveContinueWatchingDaysCap)
    }

    private fun loadString(key: String): String? = store.getString(ProfileScopedKey.of(key))

    private fun saveString(key: String, value: String) {
        store.putString(ProfileScopedKey.of(key), value)
    }
}
