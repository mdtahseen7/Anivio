package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals

class MangaReaderProgressTest {
    @Test
    fun `progress key is unique per manga provider and chapter`() {
        assertEquals(
            "manga-1|provider-a|chapter-12",
            mangaReaderProgressKey(" manga-1 ", "provider-a", "chapter-12"),
        )
        assertEquals(
            "manga-1|provider-b|chapter-12",
            mangaReaderProgressKey("manga-1", "provider-b", "chapter-12"),
        )
        assertEquals(
            "manga-1|provider-a|chapter-13",
            mangaReaderProgressKey("manga-1", "provider-a", "chapter-13"),
        )
    }

    @Test
    fun `saved progress keeps page and webtoon offset`() {
        val entry = MangaReaderProgressEntry(
            key = "manga-1|provider-a|chapter-12",
            pageIndex = 11,
            webtoonOffset = 240,
        )

        assertEquals(11, entry.pageIndex)
        assertEquals(240, entry.webtoonOffset)
    }

    @Test
    fun `completed progress is distinct from resumable progress`() {
        val resumable = MangaReaderProgressEntry(
            key = "manga-1|provider-a|chapter-12",
            pageIndex = 11,
        )
        val completed = resumable.copy(completed = true)

        assertEquals(false, resumable.completed)
        assertEquals(true, completed.completed)
    }
}
