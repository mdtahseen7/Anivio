package com.nuvio.app.core.build

private val isWindowsDesktop = System.getProperty("os.name")
    ?.startsWith("Windows", ignoreCase = true)
    ?: false

actual object AppFeaturePolicy {
    actual val pluginsEnabled: Boolean = true
    actual val downloadsEnabled: Boolean = true
    actual val notificationsEnabled: Boolean = false
    actual val supportersContributorsPageEnabled: Boolean = true
    actual val aboutPageEnabled: Boolean = true
    actual val donationActionsEnabled: Boolean = true
    actual val donationProgressEnabled: Boolean = false
    actual val accountDeletionEnabled: Boolean = false
    actual val personalMediaAddonCopyEnabled: Boolean = false
    actual val p2pEnabled: Boolean = true
    actual val externalPlayerSupported: Boolean = false
    actual val trailerPlaybackMode: TrailerPlaybackMode =
        if (isWindowsDesktop) TrailerPlaybackMode.EXTERNAL else TrailerPlaybackMode.IN_APP
    actual val heroTrailerPlaybackSupported: Boolean = !isWindowsDesktop
    // The updater points at upstream NuvioMedia/NuvioDesktop (not Anivio), so
    // updating from the AppImage would replace Anivio with Nuvio and wipe
    // the Arch fixes. The AppImage runtime sets APPIMAGE; every other
    // desktop run keeps the updater. All banner / auto-check / manual-check
    // / Settings surfaces read this one flag, so they go away together.
    actual val inAppUpdaterEnabled: Boolean = System.getenv("APPIMAGE") == null
    actual val imdbRatingLogoEnabled: Boolean = true
    actual val mediaPlaybackForegroundServiceEnabled: Boolean = false
    actual val downloadForegroundServiceEnabled: Boolean = false
    actual val customServerConnectionsEnabled: Boolean = true
}
