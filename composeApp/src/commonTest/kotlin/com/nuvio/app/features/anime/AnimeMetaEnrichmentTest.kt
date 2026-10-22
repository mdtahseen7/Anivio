package com.nuvio.app.features.anime

import kotlin.test.Test
import kotlin.test.assertEquals

class AnimeMetaEnrichmentTest {
    @Test
    fun `airing schedule sizes a season when provider and ani zip counts are missing`() {
        val videos = buildEnrichedEpisodes(
            videoIdPrefix = "anilist:213805",
            episodeCount = null,
            enrichment = AnimeEnrichment(
                airingScheduleByEpisode = mapOf(4 to 1_800_000_000L, 5 to 1_800_086_400_000L),
            ),
            seriesPoster = null,
            fallbackTitle = { number -> "Episode $number" },
        )

        assertEquals(5, videos.size)
        assertEquals("anilist:213805:1:1", videos.first().id)
        assertEquals("anilist:213805:1:5", videos.last().id)
        assertEquals(1_800_086_400_000L, videos.last().airingAtEpochMs)
    }
}
