package com.nuvio.app.features.downloads

internal actual object DownloadBackgroundExecution {
    actual val isSupported: Boolean = false
    actual fun schedule(profileId: Int, downloadId: String, kind: DownloadWorkKind) = Unit
    actual fun cancel(profileId: Int, downloadId: String, kind: DownloadWorkKind) = Unit
}
