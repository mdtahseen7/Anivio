package com.nuvio.app.features.mal

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal object MalCacheStorage {
    private const val PREFERENCES_NAME = "nuvio_mal_cache"
    private const val LIST_KEY = "mal_anime_list_cache"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun load(profileId: Int): String? =
        preferences?.getString(ProfileScopedKey.of(LIST_KEY, profileId), null)

    fun save(profileId: Int, payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(LIST_KEY, profileId), payload)?.apply()
    }

    fun remove(profileId: Int) {
        preferences?.edit()?.remove(ProfileScopedKey.of(LIST_KEY, profileId))?.apply()
    }
}
