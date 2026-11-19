package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object DownloadsStorage {
    private val store = DesktopStorage.store("nuvio_downloads")


    actual fun loadMangaPayload(): String? = store.getString(ProfileScopedKey.of("manga_downloads"))
    actual fun saveMangaPayload(payload: String) {
        store.putString(ProfileScopedKey.of("manga_downloads"), payload)
    }

    actual fun loadPayload(): String? =
        store.getString(ProfileScopedKey.of("downloads"))

    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("downloads"), payload)
    }
}
