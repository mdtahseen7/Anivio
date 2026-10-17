package com.nuvio.app.features.profile

import com.nuvio.app.core.storage.ProfileScopedKey

actual object ProfileShareSettingsStorage {
    private val payloads = mutableMapOf<String, String>()

    actual fun loadPayload(): String? = payloads[ProfileScopedKey.of("payload")]

    actual fun savePayload(payload: String) {
        payloads[ProfileScopedKey.of("payload")] = payload
    }
}
