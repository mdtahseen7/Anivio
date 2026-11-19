package com.nuvio.app.features.anilist

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object AniListCacheStorage {
    private val store = DesktopStorage.store("nuvio_anilist_cache")

    actual fun loadPayload(key: String): String? = store.getString(ProfileScopedKey.of(key))

    actual fun savePayload(key: String, payload: String) {
        store.putString(ProfileScopedKey.of(key), payload)
    }

    actual fun removePayload(key: String) {
        store.remove(ProfileScopedKey.of(key))
    }
}
