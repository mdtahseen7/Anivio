package com.nuvio.app.features.details

import platform.Foundation.NSUserDefaults

actual object MangaReaderSettingsStorage {
    private const val payloadKey = "manga_reader_settings_payload"

    actual fun loadPayload(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(payloadKey)

    actual fun savePayload(payload: String) {
        NSUserDefaults.standardUserDefaults.setObject(payload, forKey = payloadKey)
    }
}
