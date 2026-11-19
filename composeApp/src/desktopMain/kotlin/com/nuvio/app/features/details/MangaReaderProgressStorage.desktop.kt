package com.nuvio.app.features.details

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object MangaReaderProgressStorage {
    private val store = DesktopStorage.store("manga_reader_progress")

    actual fun loadPayload(): String? = store.getString(ProfileScopedKey.of("payload"))
    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("payload"), payload)
    }
}
