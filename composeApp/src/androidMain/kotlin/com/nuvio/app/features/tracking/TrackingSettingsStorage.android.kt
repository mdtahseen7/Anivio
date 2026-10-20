package com.nuvio.app.features.tracking

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import com.nuvio.app.core.sync.decodeSyncInt
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncInt
import com.nuvio.app.core.sync.encodeSyncString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal actual object TrackingSettingsStorage {
    private const val preferencesName = "anivio_tracking_settings"
    private const val librarySourceModeKey = "tracking_library_source_mode"
    private const val watchProgressSourceKey = "tracking_watch_progress_source"
    private const val continueWatchingDaysCapKey = "tracking_continue_watching_days_cap"
    private const val anivioAssetsEnabledKey = "tracking_anivio_assets_enabled"
    private const val anivioMetadataFallbackEnabledKey = "tracking_anivio_metadata_fallback_enabled"
    private val syncKeys = listOf(
        librarySourceModeKey,
        watchProgressSourceKey,
        continueWatchingDaysCapKey,
        anivioAssetsEnabledKey,
        anivioMetadataFallbackEnabledKey,
    )

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadLibrarySourceMode(): String? = loadString(librarySourceModeKey)

    actual fun saveLibrarySourceMode(value: String) = saveString(librarySourceModeKey, value)

    actual fun loadWatchProgressSource(): String? = loadString(watchProgressSourceKey)

    actual fun saveWatchProgressSource(value: String) = saveString(watchProgressSourceKey, value)

    actual fun loadContinueWatchingDaysCap(): Int? =
        preferences?.let { sharedPreferences ->
            val scopedKey = ProfileScopedKey.of(continueWatchingDaysCapKey)
            if (sharedPreferences.contains(scopedKey)) {
                sharedPreferences.getInt(scopedKey, CONTINUE_WATCHING_DAYS_CAP_ALL)
            } else {
                null
            }
        }

    actual fun saveContinueWatchingDaysCap(days: Int) {
        preferences
            ?.edit()
            ?.putInt(ProfileScopedKey.of(continueWatchingDaysCapKey), days)
            ?.apply()
    }

    actual fun loadAnivioAssetsEnabled(): Boolean? = loadBoolean(anivioAssetsEnabledKey)
    actual fun saveAnivioAssetsEnabled(enabled: Boolean) = saveBoolean(anivioAssetsEnabledKey, enabled)
    actual fun loadAnivioMetadataFallbackEnabled(): Boolean? = loadBoolean(anivioMetadataFallbackEnabledKey)
    actual fun saveAnivioMetadataFallbackEnabled(enabled: Boolean) = saveBoolean(anivioMetadataFallbackEnabledKey, enabled)

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadLibrarySourceMode()?.let { put(librarySourceModeKey, encodeSyncString(it)) }
        loadWatchProgressSource()?.let { put(watchProgressSourceKey, encodeSyncString(it)) }
        loadContinueWatchingDaysCap()?.let { put(continueWatchingDaysCapKey, encodeSyncInt(it)) }
        loadAnivioAssetsEnabled()?.let { put(anivioAssetsEnabledKey, encodeSyncBoolean(it)) }
        loadAnivioMetadataFallbackEnabled()?.let { put(anivioMetadataFallbackEnabledKey, encodeSyncBoolean(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        preferences?.edit()?.apply {
            syncKeys.forEach { remove(ProfileScopedKey.of(it)) }
        }?.apply()

        payload.decodeSyncString(librarySourceModeKey)?.let(::saveLibrarySourceMode)
        payload.decodeSyncString(watchProgressSourceKey)?.let(::saveWatchProgressSource)
        payload.decodeSyncInt(continueWatchingDaysCapKey)?.let(::saveContinueWatchingDaysCap)
        payload.decodeSyncBoolean(anivioAssetsEnabledKey)?.let(::saveAnivioAssetsEnabled)
        payload.decodeSyncBoolean(anivioMetadataFallbackEnabledKey)?.let(::saveAnivioMetadataFallbackEnabled)
    }

    private fun loadString(key: String): String? =
        preferences?.getString(ProfileScopedKey.of(key), null)

    private fun loadBoolean(key: String): Boolean? = preferences?.let {
        val scopedKey = ProfileScopedKey.of(key)
        if (it.contains(scopedKey)) it.getBoolean(scopedKey, false) else null
    }

    private fun saveBoolean(key: String, enabled: Boolean) {
        preferences?.edit()?.putBoolean(ProfileScopedKey.of(key), enabled)?.apply()
    }

    private fun saveString(key: String, value: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(key), value)
            ?.apply()
    }
}
