package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.svg.SvgDecoder

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder = apply {
    components { add(SvgDecoder.Factory()) }
}