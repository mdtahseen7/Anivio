package com.nuvio.app.features.anilist

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AniListCacheStorage {
    private const val preferencesName = "nuvio_anilist_cache"
    private const val cooldownKey = "request_cooldown_until"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadCooldown(): Long = preferences?.getLong(cooldownKey, 0L) ?: 0L

    actual fun saveCooldown(untilEpochMs: Long) {
        preferences?.edit()?.putLong(cooldownKey, untilEpochMs)?.apply()
    }

    actual fun loadPayload(key: String): String? =
        preferences?.getString(ProfileScopedKey.of(key), null)

    actual fun savePayload(key: String, payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(key), payload)
            ?.apply()
    }

    actual fun removePayload(key: String) {
        preferences
            ?.edit()
            ?.remove(ProfileScopedKey.of(key))
            ?.apply()
    }
}
