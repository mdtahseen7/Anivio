package com.nuvio.app.features.player.desktop

import com.nuvio.app.features.player.PlayerControlsState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NativePlayerControlsAudioTest {
    @Test
    fun gainUsesDecibelsAndDelayUsesSignedMilliseconds() {
        val state = PlayerControlsState(audioGainDb = 8f, audioDelayMs = -450)
        val json = Json.parseToJsonElement(state.toControlsJson(isFullscreen = false)).jsonObject
        assertEquals(8.0, json.getValue("audioGainDb").jsonPrimitive.double)
        assertEquals(-450, json.getValue("audioDelayMs").jsonPrimitive.int)
    }
}
