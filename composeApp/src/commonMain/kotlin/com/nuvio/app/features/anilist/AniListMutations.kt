package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.features.tracking.TrackingListStatus
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Write side of the AniList list integration.
 *
 * Everything here funnels through `SaveMediaListEntry`, which AniList treats as an upsert keyed on
 * `mediaId` — so there is no separate "add to list" call, and setting a status on a title the user
 * has never touched creates the entry. Removal is the one exception: it needs the entry id, which
 * only exists once the title is actually on a list.
 */
internal object AniListMutations {
    private val log = Logger.withTag("AniListMutations")

    /** Maps the app's provider-neutral list statuses onto AniList's `MediaListStatus`. */
    fun wireStatus(status: TrackingListStatus): String = when (status) {
        TrackingListStatus.WATCHING -> AniListListStatus.CURRENT
        TrackingListStatus.PLAN_TO_WATCH -> AniListListStatus.PLANNING
        TrackingListStatus.ON_HOLD -> AniListListStatus.PAUSED
        TrackingListStatus.COMPLETED -> AniListListStatus.COMPLETED
        TrackingListStatus.DROPPED -> AniListListStatus.DROPPED
    }

    /** Inverse of [wireStatus]. REPEATING collapses to WATCHING, which is how the app shows it. */
    fun trackingStatus(wireValue: String?): TrackingListStatus? = when (wireValue?.uppercase()) {
        AniListListStatus.CURRENT, AniListListStatus.REPEATING -> TrackingListStatus.WATCHING
        AniListListStatus.PLANNING -> TrackingListStatus.PLAN_TO_WATCH
        AniListListStatus.PAUSED -> TrackingListStatus.ON_HOLD
        AniListListStatus.COMPLETED -> TrackingListStatus.COMPLETED
        AniListListStatus.DROPPED -> TrackingListStatus.DROPPED
        else -> null
    }

    /**
     * Sets the list status for [mediaId], creating the list entry when it does not exist yet.
     * Returns true when AniList accepted the write.
     */
    suspend fun setStatus(mediaId: Int, status: String): Boolean = runMutation("setStatus") { token ->
        AniListClient.mutate(
            mutation = """
                mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status) { id status progress }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("mediaId", mediaId)
                put("status", status)
            },
            accessToken = token,
        )
    }

    /**
     * Sets episodes-watched for [mediaId]. AniList advances the entry to COMPLETED on its own when
     * progress reaches the episode count, so callers do not need to set both.
     */
    suspend fun setProgress(mediaId: Int, progress: Int): Boolean = runMutation("setProgress") { token ->
        AniListClient.mutate(
            mutation = """
                mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
                    SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) { id status progress }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("mediaId", mediaId)
                put("progress", progress.coerceAtLeast(0))
            },
            accessToken = token,
        )
    }

    /** Sets status and progress in one request, for a "mark whole series watched" style write. */
    suspend fun setStatusAndProgress(
        mediaId: Int,
        status: String,
        progress: Int,
    ): Boolean = runMutation("setStatusAndProgress") { token ->
        AniListClient.mutate(
            mutation = """
                mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int) {
                    SaveMediaListEntry(
                        mediaId: ${'$'}mediaId,
                        status: ${'$'}status,
                        progress: ${'$'}progress
                    ) { id status progress }
                }
            """.trimIndent(),
            variables = buildJsonObject {
                put("mediaId", mediaId)
                put("status", status)
                put("progress", progress.coerceAtLeast(0))
            },
            accessToken = token,
        )
    }

    /**
     * Deletes a list entry outright. Needs the *entry* id rather than the media id, so callers
     * resolve it from the cached lists snapshot first.
     */
    suspend fun deleteEntry(entryId: Int): Boolean = runMutation("deleteEntry") { token ->
        AniListClient.mutate(
            mutation = """
                mutation (${'$'}id: Int) {
                    DeleteMediaListEntry(id: ${'$'}id) { deleted }
                }
            """.trimIndent(),
            variables = buildJsonObject { put("id", entryId) },
            accessToken = token,
        )
    }

    private suspend inline fun runMutation(
        operation: String,
        crossinline block: suspend (token: String) -> Unit,
    ): Boolean {
        val token = AniListAuthRepository.accessTokenOrNull() ?: return false
        return try {
            block(token)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "AniList $operation failed" }
            false
        }
    }
}
