package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.ANILIST_LIST_MEDIA_FIELDS
import com.nuvio.app.core.anilist.ANILIST_MEDIA_FIELDS
import com.nuvio.app.core.anilist.AniListAiringScheduleConnection
import com.nuvio.app.core.anilist.AniListAiringScheduleNode
import com.nuvio.app.core.anilist.AniListCoverImage
import com.nuvio.app.core.anilist.AniListMedia
import com.nuvio.app.core.anilist.AniListMediaDetail
import com.nuvio.app.core.anilist.AniListTitle
import com.nuvio.app.core.anilist.airingScheduleByEpisode
import com.nuvio.app.core.anilist.toMetaPreview
import com.nuvio.app.core.anilist.AniZipEpisode
import com.nuvio.app.core.anilist.AniZipImage
import com.nuvio.app.core.anilist.AniZipResponse
import com.nuvio.app.core.anilist.KitsuEpisode
import com.nuvio.app.core.anilist.TvdbEpisode
import com.nuvio.app.core.anilist.logoUrl as aniZipLogoUrl
import com.nuvio.app.core.anivio.AnivioArtwork
import com.nuvio.app.core.anivio.AnivioMedia
import com.nuvio.app.core.anivio.logoUrl
import com.nuvio.app.features.anime.AnimeEnrichment
import com.nuvio.app.features.anime.buildEnrichedEpisodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniListOutageBoundaryTest {

    @Test
    fun `catalog page contract stays within AniList limit`() {
        assertEquals(50, ANILIST_MAX_PER_PAGE)
        assertTrue(ANILIST_MAX_PER_PAGE <= 50)
        assertTrue(ANILIST_MEDIA_FIELDS.contains("title { romaji english native }"))
        assertTrue(ANILIST_MEDIA_FIELDS.contains("coverImage { extraLarge large color }"))
    }

    @Test
    fun `list query selection remains lightweight compared with detail selection`() {
        assertTrue(ANILIST_LIST_MEDIA_FIELDS.contains("id"))
        assertTrue(ANILIST_LIST_MEDIA_FIELDS.contains("title { romaji english native }"))
        assertTrue(ANILIST_LIST_MEDIA_FIELDS.contains("coverImage { extraLarge large color }"))
        assertFalse(ANILIST_LIST_MEDIA_FIELDS.contains("description"))
        assertFalse(ANILIST_LIST_MEDIA_FIELDS.contains("characters"))
        assertFalse(ANILIST_LIST_MEDIA_FIELDS.contains("staff"))
        assertFalse(ANILIST_LIST_MEDIA_FIELDS.contains("recommendations"))
        assertNotEquals(ANILIST_MEDIA_FIELDS, ANILIST_LIST_MEDIA_FIELDS)
    }

    @Test
    fun `list mapping keeps title artwork and strips AniList markup`() {
        val preview = AniListMedia(
            id = 42,
            format = "MOVIE",
            averageScore = 87,
            title = AniListTitle(english = "English title", romaji = "Romaji title"),
            coverImage = AniListCoverImage(large = "https://image.test/poster.jpg"),
            description = "<b>Synopsis</b><br>&amp; more",
            seasonYear = 2026,
        ).toMetaPreview()

        requireNotNull(preview)
        assertEquals("anilist:42", preview.id)
        assertEquals("movie", preview.type)
        assertEquals("English title", preview.name)
        assertEquals("https://image.test/poster.jpg", preview.poster)
        assertEquals("Synopsis\n& more", preview.description)
        assertEquals("2026", preview.releaseInfo)
        assertEquals("8.7", preview.imdbRating)
    }

    @Test
    fun `detail schedule maps seconds to episode milliseconds and ignores incomplete rows`() {
        val schedule = AniListMediaDetail(
            id = 42,
            airingSchedule = AniListAiringScheduleConnection(
                nodes = listOf(
                    AniListAiringScheduleNode(episode = 3, airingAt = 1_800_000_000),
                    AniListAiringScheduleNode(episode = null, airingAt = 1_800_000_001),
                    AniListAiringScheduleNode(episode = 4, airingAt = null),
                    AniListAiringScheduleNode(episode = 5, airingAt = 0),
                ),
            ),
        ).airingScheduleByEpisode()

        assertEquals(mapOf(3 to 1_800_000_000_000L), schedule)
    }

    @Test
    fun `shared episode mapping preserves provider id while keeping streams separate`() {
        val videos = buildEnrichedEpisodes(
            videoIdPrefix = "mal:99",
            episodeCount = 2,
            enrichment = AnimeEnrichment(
                aniZip = AniZipResponse(
                    episodeCount = 2,
                    episodes = mapOf(
                        "1" to AniZipEpisode(
                            title = mapOf("en" to "AniZip title"),
                            image = "https://image.test/anizip-1.jpg",
                        ),
                        "2" to AniZipEpisode(
                            title = mapOf("en" to ""),
                            image = "https://image.test/anizip-2.jpg",
                        ),
                    ),
                ),
                kitsuEpisodes = mapOf(
                    1 to KitsuEpisode(number = 1, title = "Kitsu title", thumbnailUrl = "https://image.test/kitsu-1.jpg"),
                    2 to KitsuEpisode(number = 2, title = "Kitsu fallback title"),
                ),
                tvdbEpisodes = mapOf(
                    1 to TvdbEpisode(number = 1, seasonNumber = 1, title = "TVDB title"),
                    2 to TvdbEpisode(number = 2, seasonNumber = 1, imageUrl = "https://image.test/tvdb-2.jpg"),
                ),
                airingScheduleByEpisode = mapOf(2 to 1_800_000_000_000L),
            ),
            seriesPoster = "https://image.test/series.jpg",
            fallbackTitle = { number -> "Episode $number" },
        )

        assertEquals(listOf("mal:99:1:1", "mal:99:1:2"), videos.map { it.id })
        assertEquals(listOf("TVDB title", "Kitsu fallback title"), videos.map { it.title })
        assertEquals(
            listOf("https://image.test/kitsu-1.jpg", "https://image.test/tvdb-2.jpg"),
            videos.map { it.thumbnail },
        )
        assertEquals(1_800_000_000_000L, videos[1].airingAtEpochMs)
        assertTrue(videos.all { it.streams.isEmpty() })
        assertTrue(videos.all { it.episode in 1..2 })
    }

    @Test
    fun `Anivio prefers hdtvlogo then clearlogo for branded artwork`() {
        val media = AnivioMedia(
            id = 42,
            artworks = listOf(
                AnivioArtwork(id = "clear", url = "https://image.test/clear.png", type = "clearlogo"),
                AnivioArtwork(id = "poster", url = "https://image.test/poster.png", type = "poster"),
                AnivioArtwork(id = "hdtv", url = "https://image.test/hdtv.png", type = "hdtvlogo"),
            ),
        )

        assertEquals("https://image.test/hdtv.png", media.logoUrl)
    }

    @Test
    fun `ani zip accepts common logo artwork variants`() {
        val response = AniZipResponse(
            images = listOf(
                AniZipImage(coverType = "HDTVLogo", url = "https://image.test/hdtv.png"),
            ),
        )

        assertEquals("https://image.test/hdtv.png", response.aniZipLogoUrl())
    }

    @Test
    fun `unmapped sequel does not inherit a related ani zip mapping`() {
        val related = AniZipResponse(
            images = listOf(
                AniZipImage(coverType = "Clearlogo", url = "https://image.test/jojo-logo.png"),
            ),
            episodes = mapOf("1" to AniZipEpisode(image = "https://image.test/episode.png")),
        )

        // A prequel mapping has season 1's episode metadata and external IDs. Using it for an
        // unmapped sequel causes the sequel details screen to become a copy of the prequel.
        val sequelMapping = currentEntryAniZipMapping(direct = null)
        assertNull(sequelMapping)
        assertNotEquals(related, sequelMapping)
    }
}
