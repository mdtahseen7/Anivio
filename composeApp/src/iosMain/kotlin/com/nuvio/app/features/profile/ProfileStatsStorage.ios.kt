package com.nuvio.app.features.profile

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSUserDefaults

internal actual object ProfileStatsStorage {
    private const val keyUnlocked = "anivio_profile_achievements_unlocked"
    private const val keyLastSeenLevel = "anivio_profile_last_seen_level"

    private val defaults get() = NSUserDefaults.standardUserDefaults

    actual fun loadUnlockedPayload(): String? =
        defaults.stringForKey(ProfileScopedKey.of(keyUnlocked))

    actual fun saveUnlockedPayload(payload: String) {
        defaults.setObject(payload, forKey = ProfileScopedKey.of(keyUnlocked))
    }

    actual fun loadLastSeenLevel(): Int =
        defaults.integerForKey(ProfileScopedKey.of(keyLastSeenLevel)).toInt()

    actual fun saveLastSeenLevel(level: Int) {
        defaults.setInteger(level.toLong(), forKey = ProfileScopedKey.of(keyLastSeenLevel))
    }
}
