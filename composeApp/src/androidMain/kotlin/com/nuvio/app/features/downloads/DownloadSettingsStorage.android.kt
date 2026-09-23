package com.nuvio.app.features.downloads

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object DownloadSettingsStorage {
    private const val preferencesName = "nuvio_download_settings"
    private const val keyDefaultServer = "default_download_server"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadDefaultServerAddonName(): String? =
        preferences?.getString(ProfileScopedKey.of(keyDefaultServer), null)

    actual fun saveDefaultServerAddonName(name: String?) {
        val key = ProfileScopedKey.of(keyDefaultServer)
        if (name.isNullOrBlank()) {
            preferences?.edit()?.remove(key)?.apply()
        } else {
            preferences?.edit()?.putString(key, name)?.apply()
        }
    }
}
