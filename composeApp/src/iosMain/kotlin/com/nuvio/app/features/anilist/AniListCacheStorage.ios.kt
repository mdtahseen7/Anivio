package com.nuvio.app.features.anilist

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object AniListCacheStorage {
    actual fun loadPayload(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(key))

    actual fun savePayload(key: String, payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = ProfileScopedKey.of(key))
    }

    actual fun removePayload(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(key))
    }
}
