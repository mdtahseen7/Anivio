package com.nuvio.app.core.anilist

import android.content.Context
import android.content.SharedPreferences

internal actual object TvdbSettingsStorage {
    private const val PREFERENCES_NAME = "nuvio_tvdb_settings"

    // Not profile-scoped: an API key is a property of the install, not of a viewing profile.
    private const val API_KEY = "tvdb_api_key"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadApiKey(): String? = preferences?.getString(API_KEY, null)

    actual fun saveApiKey(apiKey: String) {
        val editor = preferences?.edit() ?: return
        if (apiKey.isBlank()) editor.remove(API_KEY).apply() else editor.putString(API_KEY, apiKey).apply()
    }
}
