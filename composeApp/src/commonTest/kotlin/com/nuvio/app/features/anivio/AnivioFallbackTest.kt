package com.nuvio.app.features.anivio

import com.nuvio.app.core.anivio.AnivioEpisode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnivioFallbackTest {
    @Test
    fun episodeListUsesCanonicalIdsAndLeavesStreamsForExistingProviders() {
        val videos = buildAnivioEpisodeVideos(
            id = "anilist:123",
            type = "series",
            episodeCount = 4,
            episodes = listOf(
                AnivioEpisode(
                    id = "episode-2",
                    number = 2,
                    title = "Second episode",
                    overview = "Overview",
                    runtime = 24,
                ),
            ),
        )

        assertEquals(listOf("anilist:123:1:2"), videos.map { it.id })
        assertEquals("Second episode", videos.single().title)
        assertEquals(2, videos.single().episode)
        assertTrue(videos.single().streams.isEmpty())
    }

    @Test
    fun `missing episode list falls back to episode count without creating an unbounded range`() {
        val videos = buildAnivioEpisodeVideos(
            id = "anilist:123",
            type = "series",
            episodeCount = 25,
            episodes = emptyList(),
        )

        assertEquals(25, videos.size)
        assertEquals("anilist:123:1:1", videos.first().id)
        assertEquals("anilist:123:1:25", videos.last().id)
    }
}
