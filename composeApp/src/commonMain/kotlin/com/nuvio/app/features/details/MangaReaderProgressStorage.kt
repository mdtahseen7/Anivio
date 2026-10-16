package com.nuvio.app.features.details

internal expect object MangaReaderProgressStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}
