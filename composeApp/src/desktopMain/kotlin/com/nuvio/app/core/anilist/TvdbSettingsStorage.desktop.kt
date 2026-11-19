package com.nuvio.app.core.anilist

import com.nuvio.app.core.storage.DesktopStorage

internal actual object TvdbSettingsStorage {
    // Not profile-scoped: an API key is a property of the install, not of a viewing profile.
    private const val API_KEY = "tvdb_api_key"
    private val store = DesktopStorage.store("nuvio_tvdb_settings")

    actual fun loadApiKey(): String? = store.getString(API_KEY)

    actual fun saveApiKey(apiKey: String) {
        store.putString(API_KEY, apiKey.takeIf { it.isNotBlank() })
    }
}
