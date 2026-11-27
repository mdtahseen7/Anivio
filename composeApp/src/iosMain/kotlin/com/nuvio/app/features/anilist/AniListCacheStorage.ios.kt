package com.nuvio.app.features.anilist

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object AniListCacheStorage {
    private const val cooldownKey = "anilist_request_cooldown_until"

    actual fun loadCooldown(): Long =
        NSUserDefaults.standardUserDefaults.stringForKey(cooldownKey)?.toLongOrNull() ?: 0L

    actual fun saveCooldown(untilEpochMs: Long) {
        NSUserDefaults.standardUserDefaults.setObject(untilEpochMs.toString(), forKey = cooldownKey)
    }

    actual fun loadPayload(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(key))

    actual fun savePayload(key: String, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(key))
    }

    actual fun removePayload(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(key))
    }
}
