package com.nuvio.app.features.anilist

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AniListCacheStorage {
    private val store = DesktopStorage.store("nuvio_anilist_cache")
    private const val cooldownKey = "request_cooldown_until"

    actual fun loadCooldown(): Long = store.getString(cooldownKey)?.toLongOrNull() ?: 0L

    actual fun saveCooldown(untilEpochMs: Long) {
        store.putString(cooldownKey, untilEpochMs.toString())
    }

    actual fun loadPayload(key: String): String? = store.getString(ProfileScopedKey.of(key))

    actual fun savePayload(key: String, payload: String) {
        store.putString(ProfileScopedKey.of(key), payload)
    }

    actual fun removePayload(key: String) {
        store.remove(ProfileScopedKey.of(key))
    }
}
