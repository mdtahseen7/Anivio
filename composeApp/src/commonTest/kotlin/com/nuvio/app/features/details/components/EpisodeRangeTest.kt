package com.nuvio.app.features.details.components

import com.nuvio.app.features.details.MetaVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpisodeRangeTest {
    @Test
    fun longSeriesIsSplitIntoFiftyEpisodeRanges() {
        val episodes = (1..1122).map { episode ->
            MetaVideo(id = "e$episode", title = "Episode $episode", season = 1, episode = episode)
        }

        val ranges = buildEpisodeRanges(episodes)

        assertEquals(EpisodeRange(1, 50), ranges.first())
        assertEquals(EpisodeRange(51, 100), ranges[1])
        assertEquals(EpisodeRange(1101, 1122), ranges.last())
        assertTrue(777 in ranges.first { 777 in it })
    }

    @Test
    fun shortSeasonDoesNotShowRangeSelector() {
        val episodes = (1..24).map { episode ->
            MetaVideo(id = "e$episode", title = "Episode $episode", season = 1, episode = episode)
        }

        assertEquals(emptyList(), buildEpisodeRanges(episodes))
    }
}
