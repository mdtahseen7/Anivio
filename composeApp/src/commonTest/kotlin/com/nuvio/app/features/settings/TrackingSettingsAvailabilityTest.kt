package com.nuvio.app.features.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackingSettingsAvailabilityTest {
    @Test
    fun `MAL availability follows its connected state`() {
        assertFalse(
            isTrackingBrandAvailable(
                brand = TrackingBrand.MAL,
                aniListConnected = true,
                malConnected = false,
            ),
        )
        assertTrue(
            isTrackingBrandAvailable(
                brand = TrackingBrand.MAL,
                aniListConnected = false,
                malConnected = true,
            ),
        )
    }
}
