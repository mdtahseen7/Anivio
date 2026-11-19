package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nuvio.app.features.player.desktop.DesktopPlayerPictureInPicture

internal actual val playerOverlayUsesWindow: Boolean = true

@Composable
internal actual fun PlatformPlayerOverlay(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val pictureInPictureVersion by DesktopPlayerPictureInPicture.changes.collectAsState()
    if (pictureInPictureVersion >= 0 && DesktopPlayerPictureInPicture.isEnabled) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        // A native Windows video surface is drawn above the Java window's Skia canvas.
        // Use an owned, transparent popup so the shared Compose controls stay interactive.
        Popup(
            alignment = Alignment.TopStart,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false,
            ),
        ) {
            Box(modifier.requiredSize(width, height), content = content)
        }
    }
}
