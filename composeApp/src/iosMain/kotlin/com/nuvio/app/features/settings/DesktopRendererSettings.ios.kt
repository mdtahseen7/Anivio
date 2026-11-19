package com.nuvio.app.features.settings

internal actual object DesktopRendererSettings {
    actual val isSupported = false
    actual val useOpenGl = false
    actual val isExternallyControlled = false
    actual fun setUseOpenGl(enabled: Boolean) = Unit
}
