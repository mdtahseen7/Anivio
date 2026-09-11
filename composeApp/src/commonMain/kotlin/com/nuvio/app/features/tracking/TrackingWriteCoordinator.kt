package com.nuvio.app.features.tracking

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

data class TrackingWriteSuccess(
    val providerId: TrackingProviderId,
    val result: TrackingMutationResult,
)

data class TrackingWriteFailure(
    val providerId: TrackingProviderId,
    val cause: Throwable,
)

data class TrackingWriteDispatchResult(
    val successes: List<TrackingWriteSuccess> = emptyList(),
    val failures: List<TrackingWriteFailure> = emptyList(),
)

/** Dispatches one provider-neutral list status to every connected list writer. */
suspend fun dispatchTrackingListStatus(
    writers: Collection<TrackingListWriter>,
    profileId: Int,
    items: Collection<TrackingMediaReference>,
    destination: TrackingListStatus?,
): TrackingWriteDispatchResult = supervisorScope {
    val outcomes = writers.map { writer ->
        async {
            try {
                val result = if (destination == null) {
                    writer.removeFromList(profileId = profileId, items = items)
                } else {
                    writer.moveToList(profileId = profileId, items = items, destination = destination)
                }
                TrackingWriteSuccess(writer.providerId, result) to null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null to TrackingWriteFailure(writer.providerId, error)
            }
        }
    }.awaitAll()
    TrackingWriteDispatchResult(
        successes = outcomes.mapNotNull { it.first },
        failures = outcomes.mapNotNull { it.second },
    )
}

object TrackingWriteCoordinator {
    suspend fun setListStatus(
        profileId: Int,
        item: TrackingMediaReference,
        destination: TrackingListStatus?,
    ): TrackingWriteDispatchResult {
        TrackingProviderRegistry.ensureLoaded()
        return dispatchTrackingListStatus(
            writers = TrackingProviderRegistry.connectedListWriters(),
            profileId = profileId,
            items = listOf(item),
            destination = destination,
        )
    }
}
