package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object DownloadSettingsStorage {
    private const val keyDefaultServer = "default_download_server"

    actual fun loadDefaultServerAddonName(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(keyDefaultServer))

    actual fun saveDefaultServerAddonName(name: String?) {
        val key = ProfileScopedKey.of(keyDefaultServer)
        if (name.isNullOrBlank()) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(name, forKey = key)
        }
    }
}
