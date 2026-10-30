package com.nuvio.app.features.profiles

import com.nuvio.app.core.storage.ProfileScopedKey

actual object ProfileBackgroundAppearanceStorage {
    private const val BrightnessKey = "profile_background_brightness"
    private val values = mutableMapOf<String, Float>()

    actual fun loadBrightness(profileId: Int): Float? =
        values[ProfileScopedKey.of(BrightnessKey, profileId)]

    actual fun saveBrightness(profileId: Int, brightness: Float) {
        values[ProfileScopedKey.of(BrightnessKey, profileId)] = brightness
    }
}
