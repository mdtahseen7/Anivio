package com.nuvio.app.core.ui

import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

// SVG is deliberately not decoded on Android — the platform SvgDecoder throws
// IllegalStateException("Android platform doesn't support SVG format") for many
// SVGs and crashes composition (licenses page, etc.). Let Coil return Error
// and show placeholder instead of crashing. iOS still decodes SVG via platform loader.
internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder =
    components {
        if (Build.VERSION.SDK_INT >= 28) {
            add(AnimatedImageDecoder.Factory())
        } else {
            add(GifDecoder.Factory())
        }
    }