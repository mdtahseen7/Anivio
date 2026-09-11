package com.nuvio.app.features.mal

import com.nuvio.app.core.mal.MalAnimeListEntry
import com.nuvio.app.core.mal.MalAnimeNode
import com.nuvio.app.core.mal.MalListStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MalTrackingTest {
    @Test
    fun `cache is account bound and round trips newest first`() {
        val snapshot = MalListsSnapshot(
            entries = listOf(entry(1, "2026-01-01T00:00:00Z"), entry(2, "2026-02-01T00:00:00Z")),
            loadedAtEpochMs = NOW,
        )
        val payload = MalListCache.encodePayload(snapshot, ACCOUNT_ID, NOW)

        assertEquals(
            listOf(2, 1),
            requireNotNull(MalListCache.decodePayload(payload, ACCOUNT_ID, NOW)).entries.map { it.node.id },
        )
        assertNull(MalListCache.decodePayload(payload, ACCOUNT_ID + 1, NOW))
    }

    @Test
    fun `cache rejects stale malformed and empty payloads`() {
        val payload = MalListCache.encodePayload(
            MalListsSnapshot(listOf(entry(1, "2026-01-01T00:00:00Z")), NOW),
            ACCOUNT_ID,
            NOW,
        )
        assertNull(MalListCache.decodePayload(payload, ACCOUNT_ID, NOW + 15L * 24 * 60 * 60 * 1000))
        assertNull(MalListCache.decodePayload("{bad", ACCOUNT_ID, NOW))
        val empty = MalListCache.encodePayload(MalListsSnapshot(), ACCOUNT_ID, NOW)
        assertNull(MalListCache.decodePayload(empty, ACCOUNT_ID, NOW))
    }

    @Test
    fun `maps all five statuses`() {
        assertEquals("watching", MalMutations.wireStatus(com.nuvio.app.features.tracking.TrackingListStatus.WATCHING))
        assertEquals("plan_to_watch", MalMutations.wireStatus(com.nuvio.app.features.tracking.TrackingListStatus.PLAN_TO_WATCH))
        assertEquals("on_hold", MalMutations.wireStatus(com.nuvio.app.features.tracking.TrackingListStatus.ON_HOLD))
        assertEquals("completed", MalMutations.wireStatus(com.nuvio.app.features.tracking.TrackingListStatus.COMPLETED))
        assertEquals("dropped", MalMutations.wireStatus(com.nuvio.app.features.tracking.TrackingListStatus.DROPPED))
    }

    @Test
    fun `only explicit MAL catalog ids are parsed`() {
        assertEquals(42, malAnimeIdOf("MAL:42:1:3"))
        assertNull(malAnimeIdOf("anilist:42"))
        assertNull(malAnimeIdOf("42"))
        assertNull(malAnimeIdOf("mal:not-a-number"))
    }

    @Test
    fun `completed entry projects all known episodes`() {
        val items = entry(7, "2026-01-01T00:00:00Z", status = "completed", progress = 1, episodes = 3)
            .toWatchedItems()
        assertEquals(listOf(1, 2, 3), items.map { it.episode })
        assertEquals("mal:7", items.first().id)
    }

    private fun entry(
        id: Int,
        updatedAt: String,
        status: String = "watching",
        progress: Int = 2,
        episodes: Int = 12,
    ) = MalAnimeListEntry(
        node = MalAnimeNode(id = id, title = "Anime $id", mediaType = "tv", numEpisodes = episodes),
        listStatus = MalListStatus(status, numEpisodesWatched = progress, updatedAt = updatedAt),
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val ACCOUNT_ID = 42
    }
}
