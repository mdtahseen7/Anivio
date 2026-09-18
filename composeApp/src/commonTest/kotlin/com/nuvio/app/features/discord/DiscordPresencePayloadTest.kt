package com.nuvio.app.features.discord

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscordPresencePayloadTest {
    private val applicationId = "1518553737218752574"

    @Test
    fun `playing series episode carries name, episode details, state and assets`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(
                contentTitle = "Frieren",
                seasonNumber = 1,
                episodeNumber = 12,
                episodeTitle = "A Real Hero",
                imageUrl = null,
                isPlaying = true,
                positionMs = 0L,
            ),
            applicationId = applicationId,
            largeImageKey = "mp:external/cover",
            startEpochMs = 1_000_000L,
        )

        assertEquals("Frieren", payload["name"]?.jsonPrimitive?.content)
        assertEquals("3", payload["type"]?.jsonPrimitive?.content)
        assertEquals("Episode S1E12 — A Real Hero", payload["details"]?.jsonPrimitive?.content)
        assertEquals("Watching on Anivio", payload["state"]?.jsonPrimitive?.content)
        assertEquals(applicationId, payload["application_id"]?.jsonPrimitive?.content)
        assertEquals("1000000", payload["timestamps"]!!.jsonObject["start"]?.jsonPrimitive?.content)
        val assets = payload["assets"]!!.jsonObject
        assertEquals("mp:external/cover", assets["large_image"]?.jsonPrimitive?.content)
        assertEquals("Frieren", assets["large_text"]?.jsonPrimitive?.content)
    }

    @Test
    fun `episode number without season falls back to plain episode details`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(
                contentTitle = "Movie Night",
                seasonNumber = null,
                episodeNumber = 5,
                episodeTitle = null,
                isPlaying = false,
            ),
            applicationId = applicationId,
            largeImageKey = null,
            startEpochMs = null,
        )

        assertEquals("Episode 5", payload["details"]?.jsonPrimitive?.content)
        assertEquals("Paused", payload["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `missing episode numbers keep details to the episode title only`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(
                contentTitle = "Interstellar",
                episodeTitle = "Feature Film",
                isPlaying = true,
            ),
            applicationId = applicationId,
            largeImageKey = null,
            startEpochMs = null,
        )

        assertEquals("Episode — Feature Film", payload["details"]?.jsonPrimitive?.content)
        assertEquals("Watching on Anivio", payload["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `blank episode title is dropped from details`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(
                contentTitle = "Interstellar",
                seasonNumber = null,
                episodeNumber = null,
                episodeTitle = "   ",
                isPlaying = true,
            ),
            applicationId = applicationId,
            largeImageKey = null,
            startEpochMs = null,
        )

        assertEquals("Episode", payload["details"]?.jsonPrimitive?.content)
    }

    @Test
    fun `blank content title falls back to the app name`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(contentTitle = " ", isPlaying = true),
            applicationId = applicationId,
            largeImageKey = null,
            startEpochMs = null,
        )

        assertEquals("Anivio", payload["name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `no artwork or elapsed time omits assets and timestamps`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(contentTitle = "Frieren", isPlaying = true),
            applicationId = applicationId,
            largeImageKey = null,
            startEpochMs = null,
        )

        assertNull(payload["assets"])
        assertNull(payload["timestamps"])
    }

    @Test
    fun `paused playback never sends a start timestamp`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(contentTitle = "Frieren", isPlaying = false, positionMs = 30_000L),
            applicationId = applicationId,
            largeImageKey = "mp:external/cover",
            startEpochMs = null,
        )

        assertNull(payload["timestamps"])
        assertEquals("Paused", payload["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `payload is serializable as a Discord activity object`() {
        val payload = buildDiscordWatchingActivity(
            activity = DiscordWatchingActivity(contentTitle = "Frieren", isPlaying = true),
            applicationId = applicationId,
            largeImageKey = null,
            startEpochMs = null,
        )

        val keys = payload.jsonObject.keys
        assertTrue(keys.containsAll(setOf("name", "type", "details", "state", "application_id")))
        assertFalse(payload.toString().isEmpty())
    }
}
