package com.nuvio.app.features.profile

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform image-sharing. The Profile screen renders a card to an [ImageBitmap] (via a graphics
 * layer) and hands it here to be written out and shared through the OS share sheet. No-op if the
 * platform can't share.
 */
internal expect object ProfileShare {
    /**
     * Share [image] as a PNG via the OS share sheet. [fileName] is a suggested base name (no
     * extension). Returns true when a share intent/sheet was presented.
     */
    suspend fun shareImage(image: ImageBitmap, fileName: String, caption: String): Boolean
}
