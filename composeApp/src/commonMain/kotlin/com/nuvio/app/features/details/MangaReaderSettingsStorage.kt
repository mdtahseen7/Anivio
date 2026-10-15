package com.nuvio.app.features.details

internal expect object MangaReaderSettingsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
