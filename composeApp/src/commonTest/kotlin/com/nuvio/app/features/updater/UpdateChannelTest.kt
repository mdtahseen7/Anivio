package com.nuvio.app.features.updater

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateChannelTest {
    @Test fun stableExcludesPreviewsEvenWhenGitHubFlagIsMissing() {
        assertFalse(isReleaseInUpdateChannel(UpdateChannel.STABLE, false, false, "v1.2.0-beta"))
        assertFalse(isReleaseInUpdateChannel(UpdateChannel.STABLE, false, false, "v1.2.0-rc1"))
        assertFalse(isReleaseInUpdateChannel(UpdateChannel.STABLE, false, true, "v1.2.0"))
        assertTrue(isReleaseInUpdateChannel(UpdateChannel.STABLE, false, false, "v1.2.0"))
    }
    @Test fun betaAcceptsReleasesAndPreviewsButNeverDrafts() {
        assertTrue(isReleaseInUpdateChannel(UpdateChannel.BETA, false, true, "v1.2.0-beta"))
        assertTrue(isReleaseInUpdateChannel(UpdateChannel.BETA, false, false, "v1.2.0"))
        assertFalse(isReleaseInUpdateChannel(UpdateChannel.BETA, true, false, "v1.2.0"))
    }
}
