package com.nuvio.app.core.build

import com.nuvio.app.features.updater.UpdateChannelConfig

actual object AppFeaturePolicy {
    actual val pluginsEnabled: Boolean = true
    actual val aboutPageEnabled: Boolean = true
    actual val donationActionsEnabled: Boolean = true
    actual val donationProgressEnabled: Boolean = false
    actual val accountDeletionEnabled: Boolean = false
    actual val personalMediaAddonCopyEnabled: Boolean = false
    actual val p2pEnabled: Boolean = true
    actual val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    actual val heroTrailerPlaybackSupported: Boolean = true
    // Sideloaded builds are the only ones allowed to self-update, and only once a release channel
    // has actually been configured. Unset ANIVIO_UPDATE_GITHUB_* properties keep this off, so the
    // app never polls the upstream Nuvio feed it used to be pinned to.
    actual val inAppUpdaterEnabled: Boolean = UpdateChannelConfig.isConfigured
    actual val imdbRatingLogoEnabled: Boolean = true
    actual val mediaPlaybackForegroundServiceEnabled: Boolean = true
    actual val customServerConnectionsEnabled: Boolean = true
}
