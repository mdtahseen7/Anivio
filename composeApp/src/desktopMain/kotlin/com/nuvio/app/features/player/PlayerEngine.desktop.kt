package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.nuvio.app.features.player.desktop.DesktopPlayerPictureInPicture
import com.nuvio.app.features.player.desktop.NativePlayerController
import com.nuvio.app.features.player.desktop.NativePlayerHost
import com.nuvio.app.features.player.desktop.desktopFullscreenChanges
import com.nuvio.app.features.streams.StreamSubtitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

@Composable
internal actual fun rememberAudioOutputKey(enabled: Boolean): String? =
    "windows:default".takeIf { enabled }

@Composable
actual fun PlatformPlayerSurface(
    sourceUrl: String,
    sourceAudioUrl: String?,
    sourceHeaders: Map<String, String>,
    sourceResponseHeaders: Map<String, String>,
    externalSubtitles: List<StreamSubtitle>,
    streamType: String?,
    useYoutubeChunkedPlayback: Boolean,
    playbackEngine: AndroidPlaybackEngine?,
    modifier: Modifier,
    playWhenReady: Boolean,
    initialPositionMs: Long?,
    initialPositionRequestKey: String?,
    resizeMode: PlayerResizeMode,
    useNativeController: Boolean,
    onInitialPositionHandled: (String, Boolean) -> Unit,
    onControllerReady: (PlayerEngineController) -> Unit,
    onSnapshot: (PlayerPlaybackSnapshot) -> Unit,
    onError: (String?) -> Unit,
) {
    val host = remember { NativePlayerHost() }
    val controller = remember(host) { NativePlayerController(host) }
    val ready = remember { mutableStateOf(false) }
    val latestOnError = rememberUpdatedState(onError)
    val latestOnSnapshot = rememberUpdatedState(onSnapshot)
    val latestOnInitialPosition = rememberUpdatedState(onInitialPositionHandled)
    val latestOnControllerReady = rememberUpdatedState(onControllerReady)

    DisposableEffect(host, controller) {
        DesktopPlayerPictureInPicture.setHost(host)
        DesktopPlayerPictureInPicture.setController(controller)
        host.onFirstFullSizePaint = { ready.value = true }
        onDispose {
            host.onFirstFullSizePaint = null
            DesktopPlayerPictureInPicture.release()
            controller.dispose()
        }
    }

    LaunchedEffect(controller, sourceUrl, sourceHeaders, ready.value) {
        if (!ready.value || sourceUrl.isBlank()) return@LaunchedEffect
        controller.attach(
            sourceUrl = sourceUrl,
            sourceHeaders = sanitizePlaybackHeaders(sourceHeaders),
            playWhenReady = playWhenReady,
            initialPositionMs = initialPositionMs ?: 0L,
            decoderPriority = 0,
            nvidiaRtxSuperResolutionEnabled = false,
            onError = { latestOnError.value(it) },
        )
        initialPositionRequestKey?.let { latestOnInitialPosition.value(it, (initialPositionMs ?: 0L) > 0L) }
        latestOnControllerReady.value(controller)
    }
    LaunchedEffect(controller, playWhenReady) {
        if (playWhenReady) controller.play() else controller.pause()
    }
    LaunchedEffect(controller, resizeMode) {
        controller.setResizeMode(resizeMode)
    }
    LaunchedEffect(controller) {
        desktopFullscreenChanges.drop(1).collect {
            controller.onDesktopFullscreenChanged()
        }
    }
    LaunchedEffect(controller) {
        while (true) {
            latestOnSnapshot.value(controller.snapshot())
            delay(250)
        }
    }

    SwingPanel(
        factory = { host },
        modifier = modifier.fillMaxSize().background(Color.Black),
    )
}
