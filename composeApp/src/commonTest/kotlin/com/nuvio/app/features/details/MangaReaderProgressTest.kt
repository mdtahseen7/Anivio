package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals
import com.nuvio.app.features.plugins.MangaPluginPage

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

    @Test
    fun `bookmark toggle adds and removes one exact reader location`() {
        val key = "manga-1|provider-a|chapter-12"
        val first = toggleMangaReaderBookmark(
            bookmarks = emptyList(),
            key = key,
            pageIndex = -2,
            webtoonOffset = -10,
            createdAtEpochMs = 100L,
        )
        assertEquals(
            MangaReaderBookmark(key = key, pageIndex = 0, webtoonOffset = 0, createdAtEpochMs = 100L),
            first.single(),
        )
        assertEquals(
            emptyList(),
            toggleMangaReaderBookmark(first, key, pageIndex = 0, webtoonOffset = 0),
        )
    }

    @Test
    fun `bookmarks remain independent for chapters and webtoon offsets`() {
        val bookmarks = listOf(
            MangaReaderBookmark("chapter-1", pageIndex = 2, webtoonOffset = 10),
        )
        val updated = toggleMangaReaderBookmark(
            bookmarks,
            key = "chapter-2",
            pageIndex = 2,
            webtoonOffset = 10,
        )

        assertEquals(2, updated.size)
        assertEquals(2, updated.count { it.pageIndex == 2 && it.webtoonOffset == 10 })
    }

    @Test
    fun `manga page keeps provider request headers`() {
        assertEquals(
            mapOf("Referer" to "https://reader.example/", "User-Agent" to "Anivio"),
            MangaPluginPage(
                url = "https://reader.example/page.jpg",
                headers = mapOf("Referer" to "https://reader.example/", "User-Agent" to "Anivio"),
            ).headers,
        )
    }
}
