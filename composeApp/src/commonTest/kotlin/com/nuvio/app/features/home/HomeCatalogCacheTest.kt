package com.nuvio.app.features.home

import com.nuvio.app.features.catalog.CatalogTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeCatalogCacheTest {

    @Test
    fun `round trips rows and hero artwork`() {
        val payload = HomeCatalogCache.encodePayload(
            sections = mapOf("anilist:trending|v1" to section(items = listOf(preview("anilist:1")))),
            heroArtwork = mapOf("series:anilist:1" to preview("anilist:1", logo = "https://logo")),
            nowEpochMs = NOW,
        )

        val snapshot = requireNotNull(payload).let {
            HomeCatalogCache.decodePayload(payload = it, nowEpochMs = NOW)
        }

        val row = requireNotNull(snapshot?.rows?.get("anilist:trending|v1"))
        assertEquals(listOf("anilist:1"), row.items.map { it.id })
        assertEquals(24, row.availableItemCount)
        assertTrue(row.hasMore)
        assertEquals("https://logo", snapshot?.heroArtwork?.get("series:anilist:1")?.logo)
    }

    @Test
    fun `nothing is stored when there are no rows and no artwork`() {
        assertNull(
            HomeCatalogCache.encodePayload(
                sections = mapOf("anilist:trending|v1" to section(items = emptyList())),
                heroArtwork = emptyMap(),
                nowEpochMs = NOW,
            ),
        )
    }

    @Test
    fun `payload older than the maximum age is dropped`() {
        val payload = requireNotNull(
            HomeCatalogCache.encodePayload(
                sections = mapOf("anilist:trending|v1" to section(items = listOf(preview("anilist:1")))),
                heroArtwork = emptyMap(),
                nowEpochMs = NOW,
            ),
        )

        val eightDaysLater = NOW + 8L * 24 * 60 * 60 * 1000
        assertNull(HomeCatalogCache.decodePayload(payload = payload, nowEpochMs = eightDaysLater))
    }

    @Test
    fun `payload from another cache version is dropped`() {
        val payload = """{"version":99,"storedAtEpochMs":$NOW,"rows":[],"heroArtwork":[]}"""

        assertNull(HomeCatalogCache.decodePayload(payload = payload, nowEpochMs = NOW))
    }

    @Test
    fun `unreadable payload is discarded rather than thrown`() {
        assertNull(HomeCatalogCache.decodePayload(payload = "{not json", nowEpochMs = NOW))
    }

    private fun section(items: List<MetaPreview>): HomeCatalogSection =
        HomeCatalogSection(
            key = "anilist:trending",
            title = "Trending",
            subtitle = "AniList",
            addonName = "AniList",
            target = CatalogTarget.AniList(
                catalogId = "trending",
                contentType = "series",
                supportsPagination = true,
            ),
            items = items,
            availableItemCount = 24,
            hasMore = true,
        )

    private fun preview(id: String, logo: String? = null): MetaPreview =
        MetaPreview(
            id = id,
            type = "series",
            name = "Anime",
            poster = "https://poster",
            logo = logo,
        )

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
