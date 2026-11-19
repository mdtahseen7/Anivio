package com.nuvio.app.features.profile

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object ProfileStatsStorage {
    private val store = DesktopStorage.store("profile_stats")

    actual fun loadUnlockedPayload(): String? = store.getString(ProfileScopedKey.of("achievements"))
    actual fun saveUnlockedPayload(payload: String) {
        store.putString(ProfileScopedKey.of("achievements"), payload)
    }
    actual fun loadLastSeenLevel(): Int = store.getInt(ProfileScopedKey.of("last_seen_level")) ?: 0
    actual fun saveLastSeenLevel(level: Int) {
        store.putInt(ProfileScopedKey.of("last_seen_level"), level)
    }
}
