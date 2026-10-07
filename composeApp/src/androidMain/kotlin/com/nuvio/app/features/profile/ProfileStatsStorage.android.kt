package com.nuvio.app.features.profile

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object ProfileStatsStorage {
    private const val preferencesName = "anivio_profile_stats"
    private const val keyUnlocked = "achievements_unlocked"
    private const val keyLastSeenLevel = "last_seen_level"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadUnlockedPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(keyUnlocked), null)

    actual fun saveUnlockedPayload(payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(keyUnlocked), payload)?.apply()
    }

    actual fun loadLastSeenLevel(): Int =
        preferences?.getInt(ProfileScopedKey.of(keyLastSeenLevel), 0) ?: 0

    actual fun saveLastSeenLevel(level: Int) {
        preferences?.edit()?.putInt(ProfileScopedKey.of(keyLastSeenLevel), level)?.apply()
    }
}
