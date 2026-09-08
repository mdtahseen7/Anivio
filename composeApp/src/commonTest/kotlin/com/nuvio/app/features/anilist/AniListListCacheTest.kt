package com.nuvio.app.features.anilist

import com.nuvio.app.core.anilist.AniListMedia
import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.anilist.AniListTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AniListListCacheTest {

    @Test
    fun `round trips both lists newest entry first`() {
        val payload = AniListListCache.encodePayload(
            snapshot = AniListListsSnapshot(
                anime = listOf(entry(id = 1, mediaId = 11, updatedAt = 100), entry(id = 2, mediaId = 22, updatedAt = 900)),
                manga = listOf(entry(id = 3, mediaId = 33, updatedAt = 500)),
                loadedAtEpochMs = NOW,
            ),
            accountId = ACCOUNT_ID,
            nowEpochMs = NOW,
        )

        val snapshot = requireNotNull(
            AniListListCache.decodePayload(payload = payload, accountId = ACCOUNT_ID, nowEpochMs = NOW),
        )

        assertEquals(listOf(22, 11), snapshot.anime.map { requireNotNull(it.media).id })
        assertEquals(listOf(33), snapshot.manga.map { requireNotNull(it.media).id })
        assertEquals(NOW, snapshot.loadedAtEpochMs)
    }

    @Test
    fun `lists written for another account are not served`() {
        val payload = AniListListCache.encodePayload(
            snapshot = AniListListsSnapshot(anime = listOf(entry(id = 1, mediaId = 11, updatedAt = 100))),
            accountId = ACCOUNT_ID,
            nowEpochMs = NOW,
        )

        assertNull(AniListListCache.decodePayload(payload = payload, accountId = 999, nowEpochMs = NOW))
    }

    @Test
    fun `payload older than the maximum age is dropped`() {
        val payload = AniListListCache.encodePayload(
            snapshot = AniListListsSnapshot(anime = listOf(entry(id = 1, mediaId = 11, updatedAt = 100))),
            accountId = ACCOUNT_ID,
            nowEpochMs = NOW,
        )

        val fifteenDaysLater = NOW + 15L * 24 * 60 * 60 * 1000
        assertNull(
            AniListListCache.decodePayload(
                payload = payload,
                accountId = ACCOUNT_ID,
                nowEpochMs = fifteenDaysLater,
            ),
        )
    }

    @Test
    fun `empty lists are not treated as a usable cache`() {
        val payload = AniListListCache.encodePayload(
            snapshot = AniListListsSnapshot(),
            accountId = ACCOUNT_ID,
            nowEpochMs = NOW,
        )

        assertNull(AniListListCache.decodePayload(payload = payload, accountId = ACCOUNT_ID, nowEpochMs = NOW))
    }

    @Test
    fun `unreadable payload is discarded rather than thrown`() {
        assertNull(
            AniListListCache.decodePayload(payload = "{not json", accountId = ACCOUNT_ID, nowEpochMs = NOW),
        )
    }

    private fun entry(id: Int, mediaId: Int, updatedAt: Long): AniListMediaListEntry =
        AniListMediaListEntry(
            id = id,
            status = AniListListStatus.CURRENT,
            progress = 3,
            updatedAt = updatedAt,
            media = AniListMedia(
                id = mediaId,
                type = "ANIME",
                title = AniListTitle(romaji = "Anime $mediaId"),
            ),
        )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ACCOUNT_ID = 42
    }
}
