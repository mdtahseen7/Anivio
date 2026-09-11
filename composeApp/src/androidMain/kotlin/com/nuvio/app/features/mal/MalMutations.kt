package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.core.mal.MalClient
import com.nuvio.app.features.tracking.TrackingListStatus
import kotlinx.coroutines.CancellationException

internal object MalMutations {
    private val log = Logger.withTag("MalMutations")

    fun wireStatus(status: TrackingListStatus): String = when (status) {
        TrackingListStatus.WATCHING -> MalListStatusValue.WATCHING
        TrackingListStatus.PLAN_TO_WATCH -> MalListStatusValue.PLAN_TO_WATCH
        TrackingListStatus.ON_HOLD -> MalListStatusValue.ON_HOLD
        TrackingListStatus.COMPLETED -> MalListStatusValue.COMPLETED
        TrackingListStatus.DROPPED -> MalListStatusValue.DROPPED
    }

    fun trackingStatus(value: String?): TrackingListStatus? = when (value?.lowercase()) {
        MalListStatusValue.WATCHING -> TrackingListStatus.WATCHING
        MalListStatusValue.PLAN_TO_WATCH -> TrackingListStatus.PLAN_TO_WATCH
        MalListStatusValue.ON_HOLD -> TrackingListStatus.ON_HOLD
        MalListStatusValue.COMPLETED -> TrackingListStatus.COMPLETED
        MalListStatusValue.DROPPED -> TrackingListStatus.DROPPED
        else -> null
    }

    suspend fun setStatus(animeId: Int, status: String): Boolean = runMutation("setStatus") { token ->
        MalClient.updateAnimeListStatus(token, animeId, status = status)
    }

    suspend fun setProgress(animeId: Int, progress: Int): Boolean = runMutation("setProgress") { token ->
        MalClient.updateAnimeListStatus(token, animeId, numEpisodesWatched = progress.coerceAtLeast(0))
    }

    suspend fun setStatusAndProgress(animeId: Int, status: String, progress: Int): Boolean =
        runMutation("setStatusAndProgress") { token ->
            MalClient.updateAnimeListStatus(
                token,
                animeId,
                status = status,
                numEpisodesWatched = progress.coerceAtLeast(0),
            )
        }

    suspend fun delete(animeId: Int): Boolean = runMutation("delete") { token ->
        MalClient.deleteAnimeListStatus(token, animeId)
    }

    private suspend inline fun runMutation(
        operation: String,
        crossinline block: suspend (String) -> Unit,
    ): Boolean {
        val token = MalAuthRepository.accessTokenOrNull() ?: return false
        return try {
            block(token)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "MAL $operation failed" }
            false
        }
    }
}
