package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

internal val AppIconOption.labelResource: StringResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.string.settings_appearance_app_icon_original
        AppIconOption.ARCTIC_BLUE -> Res.string.settings_appearance_app_icon_arctic_blue
        AppIconOption.EMERALD -> Res.string.settings_appearance_app_icon_emerald
        AppIconOption.ROSE_GOLD -> Res.string.settings_appearance_app_icon_rose_gold
        AppIconOption.COPPER -> Res.string.settings_appearance_app_icon_copper
        AppIconOption.GRAPHITE -> Res.string.settings_appearance_app_icon_graphite
    }

internal val AppIconOption.previewResource: DrawableResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.drawable.app_icon_original
        AppIconOption.ARCTIC_BLUE -> Res.drawable.app_icon_arctic_blue
        AppIconOption.EMERALD -> Res.drawable.app_icon_emerald
        AppIconOption.ROSE_GOLD -> Res.drawable.app_icon_rose_gold
        AppIconOption.COPPER -> Res.drawable.app_icon_copper
        AppIconOption.GRAPHITE -> Res.drawable.app_icon_graphite
    }

/**
 * The Anivio wordmark.
 *
 * One asset for every colourway and every theme. The inherited artwork kept a separate raster per
 * colour because "Nuvio" was baked into the pixels and each copy had been recoloured by hand. The
 * Anivio wordmark is a single mark, so those copies were deleted rather than duplicated six times.
 *
 * Still expressed as a property and a function so the call sites do not change if a per-colourway
 * wordmark is ever reintroduced.
 */
internal val AppIconOption.wordmarkResource: DrawableResource
    get() = Res.drawable.app_logo_wordmark

@Suppress("UnusedReceiverParameter")
internal fun AppTheme.wordmarkResource(fallback: AppIconOption): DrawableResource =
    fallback.wordmarkResource
