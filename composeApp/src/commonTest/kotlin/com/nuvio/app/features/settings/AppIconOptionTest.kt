package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.app_logo_wordmark

class AppIconOptionTest {
    @Test
    fun primaryIconUsesPlatformDefault() {
        assertEquals(null, AppIconOption.ORIGINAL.platformName)
        assertEquals(AppIconOption.ORIGINAL, AppIconOption.fromPlatformName(null))
    }

    @Test
    fun alternateIconNamesRoundTrip() {
        AppIconOption.entries.drop(1).forEach { icon ->
            assertEquals(icon, AppIconOption.fromPlatformName(icon.platformName))
        }
    }

    @Test
    fun shortlistedCatalogueContainsSixIcons() {
        assertEquals(6, AppIconOption.entries.size)
    }

    @Test
    fun unknownIconFallsBackToOriginal() {
        assertEquals(AppIconOption.ORIGINAL, AppIconOption.fromPlatformName("UnknownIcon"))
    }

    @Test
    fun everyThemeAndIconShareOneWordmark() {
        // There is a single Anivio wordmark now, so no theme or colourway may resolve to anything
        // else. Asserted rather than assumed because the per-colour rasters were deleted, and a stray
        // reference to one would be a missing-resource crash at runtime, not a compile error.
        AppTheme.entries.forEach { theme ->
            AppIconOption.entries.forEach { icon ->
                assertEquals(Res.drawable.app_logo_wordmark, theme.wordmarkResource(icon))
            }
        }
    }
}
