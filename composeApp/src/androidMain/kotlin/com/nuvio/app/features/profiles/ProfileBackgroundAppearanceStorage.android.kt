package com.nuvio.app.features.profiles

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

actual object ProfileBackgroundAppearanceStorage {
    private const val PreferencesName = "nuvio_profile_background_appearance"
    private const val BrightnessKey = "brightness"
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadBrightness(profileId: Int): Float? {
        val preferences = preferences ?: return null
        val key = ProfileScopedKey.of(BrightnessKey, profileId)
        return if (preferences.contains(key)) preferences.getFloat(key, DEFAULT_PROFILE_BACKGROUND_BRIGHTNESS) else null
    }

    actual fun saveBrightness(profileId: Int, brightness: Float) {
        preferences?.edit()
            ?.putFloat(ProfileScopedKey.of(BrightnessKey, profileId), brightness)
            ?.apply()
    }
}
