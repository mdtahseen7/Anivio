package com.nuvio.app.features.profiles

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey

actual object ProfileBackgroundAppearanceStorage {
    private const val BrightnessKey = "profile_background_brightness"
    private val store = DesktopStorage.store("profile_appearance")

    actual fun loadBrightness(profileId: Int): Float? =
        store.getFloat(ProfileScopedKey.of(BrightnessKey, profileId))

    actual fun saveBrightness(profileId: Int, brightness: Float) {
        store.putFloat(ProfileScopedKey.of(BrightnessKey, profileId), brightness)
    }
}
