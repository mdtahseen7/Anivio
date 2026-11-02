package com.nuvio.app.features.membership

import com.nuvio.app.features.profiles.NuvioProfile
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileBackgroundTest {
    @Test
    fun savedBackgroundRendersWithoutEntitlement() {
        val profile = NuvioProfile(profileBackgroundId = "aurora")

        assertEquals(
            ProfileBackgroundSelection.Catalog("aurora"),
            resolveProfileBackground(profile, CosmeticEntitlements.None),
        )
    }

    @Test
    fun customBackgroundTakesPriorityWhenEntitled() {
        val profile = NuvioProfile(
            profileBackgroundId = "aurora",
            profileBackgroundUrl = "https://example.com/background.png",
        )
        val entitlements = CosmeticEntitlements(setOf(CosmeticEntitlement.PROFILE_BACKGROUNDS))

        assertEquals(
            ProfileBackgroundSelection.Custom("https://example.com/background.png"),
            resolveProfileBackground(profile, entitlements),
        )
    }
}
