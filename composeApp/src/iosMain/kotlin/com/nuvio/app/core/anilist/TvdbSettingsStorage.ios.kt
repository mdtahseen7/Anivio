package com.nuvio.app.core.anilist

import platform.Foundation.NSUserDefaults

internal actual object TvdbSettingsStorage {
    // Not profile-scoped: an API key is a property of the install, not of a viewing profile.
    private const val API_KEY = "tvdb_api_key"

    actual fun loadApiKey(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(API_KEY)

    actual fun saveApiKey(apiKey: String) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (apiKey.isBlank()) {
            defaults.removeObjectForKey(API_KEY)
        } else {
            defaults.setObject(apiKey, forKey = API_KEY)
        }
    }
}
