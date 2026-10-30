package com.nuvio.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileBackgroundAppearanceTest {
    @Test
    fun brightnessIsClampedToReadableRange() {
        assertEquals(MIN_PROFILE_BACKGROUND_BRIGHTNESS, normalizeProfileBackgroundBrightness(-1f))
        assertEquals(0.65f, normalizeProfileBackgroundBrightness(0.65f))
        assertEquals(MAX_PROFILE_BACKGROUND_BRIGHTNESS, normalizeProfileBackgroundBrightness(2f))
        assertEquals(DEFAULT_PROFILE_BACKGROUND_BRIGHTNESS, normalizeProfileBackgroundBrightness(Float.NaN))
    }

    @Test
    fun overlayAlphaIsInverseOfBrightness() {
        assertEquals(0.5f, profileBackgroundOverlayAlpha(0.5f))
        assertEquals(0f, profileBackgroundOverlayAlpha(1f))
    }
}
