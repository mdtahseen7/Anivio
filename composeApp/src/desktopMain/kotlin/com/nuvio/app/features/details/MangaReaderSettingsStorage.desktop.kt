package com.nuvio.app.features.details

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object MangaReaderSettingsStorage {
    private val store = DesktopStorage.store("manga_reader_settings")

    actual fun loadPayload(): String? = store.getString(ProfileScopedKey.of("payload"))
    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("payload"), payload)
    }
}
