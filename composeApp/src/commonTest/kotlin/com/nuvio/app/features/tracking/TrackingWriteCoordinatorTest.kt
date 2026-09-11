package com.nuvio.app.features.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TrackingWriteCoordinatorTest {
    @Test
    fun `dispatch preserves success when another provider fails`() = runBlocking {
        val called = mutableListOf<TrackingProviderId>()
        val success = writer(TrackingProviderId.ANILIST) { destination ->
            called += TrackingProviderId.ANILIST
            TrackingMutationResult(1, resolutions = listOf(TrackingMutationResolution(destination)))
        }
        val failure = writer(TrackingProviderId.MAL) {
            called += TrackingProviderId.MAL
            error("MAL unavailable")
        }

        val result = dispatchTrackingListStatus(
            writers = listOf(success, failure),
            profileId = 1,
            items = listOf(media()),
            destination = TrackingListStatus.ON_HOLD,
        )

        assertEquals(setOf(TrackingProviderId.ANILIST, TrackingProviderId.MAL), called.toSet())
        assertEquals(TrackingProviderId.ANILIST, result.successes.single().providerId)
        assertEquals(TrackingProviderId.MAL, result.failures.single().providerId)
    }

    @Test
    fun `dispatch sends same semantic status to every writer`() = runBlocking {
        val destinations = mutableMapOf<TrackingProviderId, TrackingListStatus>()
        val writers = TrackingProviderId.entries.map { providerId ->
            writer(providerId) { destination ->
                destinations[providerId] = destination
                TrackingMutationResult(1)
            }
        }

        dispatchTrackingListStatus(writers, 1, listOf(media()), TrackingListStatus.DROPPED)

        assertEquals(
            TrackingProviderId.entries.associateWith { TrackingListStatus.DROPPED },
            destinations,
        )
    }

    @Test
    fun `dispatch does not swallow cancellation`() {
        val cancelling = writer(TrackingProviderId.ANILIST) { throw CancellationException("cancel") }
        assertFailsWith<CancellationException> {
            runBlocking {
                dispatchTrackingListStatus(listOf(cancelling), 1, listOf(media()), TrackingListStatus.WATCHING)
            }
        }
    }

    private fun writer(
        providerId: TrackingProviderId,
        move: suspend (TrackingListStatus) -> TrackingMutationResult,
    ) = object : TrackingListWriter {
        override val providerId = providerId
        override suspend fun moveToList(
            profileId: Int,
            items: Collection<TrackingMediaReference>,
            destination: TrackingListStatus,
        ) = move(destination)

        override suspend fun removeFromList(
            profileId: Int,
            items: Collection<TrackingMediaReference>,
        ) = TrackingMutationResult(items.size)
    }

    private fun media() = TrackingMediaReference(
        kind = TrackingMediaKind.ANIME,
        ids = TrackingExternalIds(anilist = 1, mal = 2),
    )
}
