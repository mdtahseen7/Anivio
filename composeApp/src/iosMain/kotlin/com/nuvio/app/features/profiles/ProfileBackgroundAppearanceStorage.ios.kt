package com.nuvio.app.features.profiles

import com.nuvio.app.core.storage.ProfileScopedKey
import platform.Foundation.NSNumber
import platform.Foundation.NSUserDefaults

actual object ProfileBackgroundAppearanceStorage {
    private const val BrightnessKey = "profile_background_brightness"

    actual fun loadBrightness(profileId: Int): Float? =
        (NSUserDefaults.standardUserDefaults.objectForKey(ProfileScopedKey.of(BrightnessKey, profileId)) as? NSNumber)
            ?.floatValue

    actual fun saveBrightness(profileId: Int, brightness: Float) {
        NSUserDefaults.standardUserDefaults.setFloat(
            brightness,
            forKey = ProfileScopedKey.of(BrightnessKey, profileId),
        )
    }
}
