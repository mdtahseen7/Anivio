package com.nuvio.app.features.anime

import com.nuvio.app.core.anilist.AniZipEpisode
import com.nuvio.app.core.anilist.AniZipResponse
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertEquals

class AnimeMetaEnrichmentTest {
    @Test
    fun `explicit sequel titles change the label and keep the original video id`() {
        val examples = listOf(
            Triple("anilist:199111", "Grand Blue Dreaming Season 3", 3),
            Triple("anilist:195604", "Black Clover Second Season", 2),
            Triple("mal:100", "Example 3rd Season Part 2", 3),
        )
        for ((id, title, expectedSeason) in examples) {
            val video = buildEnrichedEpisodes(
                videoIdPrefix = id,
                episodeCount = 1,
                enrichment = AnimeEnrichment(),
                seriesPoster = null,
                seriesTitle = title,
                fallbackTitle = { "Episode $it" },
            ).single()
            assertEquals(expectedSeason, video.seasonForDisplay)
            assertEquals(expectedSeason, animeEpisodeSeasonForDisplay(id, title, 1))
            assertEquals(1, video.season)
            assertEquals("$id:1:1", video.id)
        }
    }

    @Test
    fun `only a single mapped season can supply a missing season label`() {
        val regular = "1" to AniZipEpisode(seasonNumber = 4)
        val special = "S1" to AniZipEpisode(seasonNumber = 0)
        assertEquals(4, animeDisplaySeason("Example Arc", AniZipResponse(episodes = mapOf(regular, special))))
        assertEquals(3, animeDisplaySeason("Example Season 3", AniZipResponse(episodes = mapOf(regular))))
        assertNull(animeDisplaySeason("Example Part 2", AniZipResponse(episodes = mapOf(
            regular,
            "2" to AniZipEpisode(seasonNumber = 5),
        ))))
        assertEquals(1, animeEpisodeSeasonForDisplay("tmdb:100", "Example Season 3", 1))
        assertEquals(0, animeEpisodeSeasonForDisplay("anilist:100", "Example Season 3", 0))
    }
}
