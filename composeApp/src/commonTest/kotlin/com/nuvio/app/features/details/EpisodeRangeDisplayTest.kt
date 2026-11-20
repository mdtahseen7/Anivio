package com.nuvio.app.features.details

import com.nuvio.app.features.details.components.episodeRangesForDisplay
import com.nuvio.app.features.details.components.preferredEpisodeRange
import kotlin.test.Test
import kotlin.test.assertEquals

class EpisodeRangeDisplayTest {
    @Test
    fun longSeriesKeepsOriginalEpisodesAndSelectsTheResumeRange() {
        val episodes = (1..1123).map { number ->
            MetaVideo(id = "episode-$number", title = "Episode $number", season = 1, episode = number)
        }

        val ranges = episodes.episodeRangesForDisplay()

        assertEquals(23, ranges.size)
        assertEquals(episodes, ranges.flatten())
        assertEquals(23, ranges.last().size)
        assertEquals(19, ranges.preferredEpisodeRange(1000))
        assertEquals(20, ranges.preferredEpisodeRange(1001))
        assertEquals(22, ranges.preferredEpisodeRange(1123))
        assertEquals(0, ranges.preferredEpisodeRange(1124))
    }
}
