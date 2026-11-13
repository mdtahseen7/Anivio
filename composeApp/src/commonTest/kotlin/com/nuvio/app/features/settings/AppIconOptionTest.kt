package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_av_wordmark

class AppIconOptionTest {
    @Test
    fun primaryIconUsesPlatformDefault() {
        assertEquals(null, AppIconOption.AV.platformName)
        assertEquals(AppIconOption.AV, AppIconOption.fromPlatformName(null))
        assertEquals("AppIconOriginal", AppIconOption.ORIGINAL.platformName)
    }

    @Test
    fun alternateIconNamesRoundTrip() {
        AppIconOption.entries.drop(1).forEach { icon ->
            assertEquals(icon, AppIconOption.fromPlatformName(icon.platformName))
        }
    }

    @Test
    fun catalogueContainsNewDefaultAndSixPreservedAlternatives() {
        assertEquals(7, AppIconOption.entries.size)
        assertEquals(AppIconOption.AV, AppIconOption.entries.first())
        assertEquals(6, AppIconOption.entries.count { it.platformName != null })
    }

    @Test
    fun unknownIconFallsBackToOriginal() {
        assertEquals(AppIconOption.AV, AppIconOption.fromPlatformName("UnknownIcon"))
    }

    @Test
    fun everyThemeAndIconShareOneWordmark() {
        // There is a single Anivio wordmark now, so no theme or colourway may resolve to anything
        // else. Asserted rather than assumed because the per-colour rasters were deleted, and a stray
        // reference to one would be a missing-resource crash at runtime, not a compile error.
        AppTheme.entries.forEach { theme ->
            AppIconOption.entries.forEach { icon ->
                assertEquals(Res.drawable.app_logo_av_wordmark, theme.wordmarkResource(icon))
            }
        }
    }
}
