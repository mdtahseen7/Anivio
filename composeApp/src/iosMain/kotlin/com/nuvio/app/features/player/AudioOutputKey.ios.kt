package com.nuvio.app.features.player

import androidx.compose.runtime.*
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue

@Composable
internal actual fun rememberAudioOutputKey(enabled: Boolean): String? {
    fun outputKey(): String = AVAudioSession.sharedInstance().currentRoute.outputs.mapNotNull { (it as? AVAudioSessionPortDescription)?.UID }.joinToString("|")
    var key by remember(enabled) { mutableStateOf(if (enabled) outputKey() else null) }
    DisposableEffect(enabled) {
        val observer = if (enabled) NSNotificationCenter.defaultCenter.addObserverForName(
            AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue,
        ) { key = outputKey() } else null
        onDispose { observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) } }
    }
    return key
}
