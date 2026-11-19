package com.nuvio.app.features.party

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyStreamSharingTest {
    @Test
    fun shareableSourcesUseHttpsHostnames() {
        assertTrue(isShareableStreamUrl("https://media.example.org/stream.m3u8"))
        assertFalse(isShareableStreamUrl("http://media.example.org/stream.m3u8"))
        assertFalse(isShareableStreamUrl("https://127.0.0.1/watch"))
        assertFalse(isShareableStreamUrl("https://server.local/watch"))
        assertFalse(isShareableStreamUrl("https://user:secret@media.example.org/watch"))
        assertFalse(isShareableStreamUrl("file:///movie.mkv"))
    }

    @Test
    fun onlyPlaybackHeadersAreShared() {
        assertEquals(
            mapOf("Origin" to "https://media.example.org"),
            partyStreamRequestHeaders(
                mapOf(
                    "Origin" to "https://media.example.org",
                    "Cookie" to "private",
                    "authorization" to "private",
                    "X-Api-Key" to "private",
                ),
            ),
        )
    }
}
