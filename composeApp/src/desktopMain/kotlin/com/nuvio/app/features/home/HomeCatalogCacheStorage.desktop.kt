package com.nuvio.app.features.home

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object HomeCatalogCacheStorage {
    private const val payloadKey = "home_catalog_cache_payload"
    private val store = DesktopStorage.store("nuvio_home_catalog_cache")

    actual fun loadPayload(): String? = store.getString(ProfileScopedKey.of(payloadKey))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of(payloadKey), payload)
    }

    actual fun clearPayload() {
        store.remove(ProfileScopedKey.of(payloadKey))
    }
}
