package com.nuvio.app.features.profile

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object ProfileShareSettingsStorage {
    private val store = DesktopStorage.store("profile_share_settings")

    actual fun loadPayload(): String? = store.getString(ProfileScopedKey.of("payload"))
    actual fun savePayload(payload: String) {
        store.putString(ProfileScopedKey.of("payload"), payload)
    }
}
