package com.nuvio.app.features.downloads

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object DownloadSettingsStorage {
    private const val keyDefaultServer = "default_download_server"
    private val store = DesktopStorage.store("nuvio_download_settings")

    actual fun loadDefaultServerAddonName(): String? =
        store.getString(ProfileScopedKey.of(keyDefaultServer))

    actual fun saveDefaultServerAddonName(name: String?) {
        store.putString(ProfileScopedKey.of(keyDefaultServer), name?.takeIf { it.isNotBlank() })
    }
}
