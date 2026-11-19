package com.nuvio.app.features.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize

interface PlayerGestureController {
    fun currentBrightness(): Float?
    fun setBrightness(level: Float): Float?
    fun currentVolume(): PlayerAudioLevel?
    fun setVolume(level: Float): PlayerAudioLevel?
}

data class PlayerAudioLevel(
    val fraction: Float,
    val isMuted: Boolean,
)

@Composable
expect fun LockPlayerToLandscape()

@Composable
expect fun EnterImmersivePlayerMode(keepScreenAwake: Boolean)

@Composable
expect fun ManagePlayerPictureInPicture(
    isPlaying: Boolean,
    videoSize: IntSize,
)

@Composable
expect fun rememberIsInPictureInPicture(): Boolean

@Composable
expect fun rememberPlayerGestureController(): PlayerGestureController?

internal expect val playerOverlayUsesWindow: Boolean

@Composable
internal expect fun PlatformPlayerOverlay(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
)
