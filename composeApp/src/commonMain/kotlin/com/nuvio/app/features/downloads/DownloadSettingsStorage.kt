package com.nuvio.app.features.downloads

internal expect object DownloadSettingsStorage {
    fun loadDefaultServerAddonName(): String?
    fun saveDefaultServerAddonName(name: String?)
}
