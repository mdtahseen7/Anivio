package com.nuvio.app.features.downloads

internal enum class DownloadWorkKind { ANIME, MANGA }

/** Platform scheduler for durable download continuation. Non-Android targets may be a no-op. */
internal expect object DownloadBackgroundExecution {
    val isSupported: Boolean
    fun schedule(profileId: Int, downloadId: String, kind: DownloadWorkKind)
    fun cancel(profileId: Int, downloadId: String, kind: DownloadWorkKind)
}
