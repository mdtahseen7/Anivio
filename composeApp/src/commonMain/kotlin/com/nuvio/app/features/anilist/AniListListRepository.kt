package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.ANILIST_LIST_MEDIA_FIELDS
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListMediaListCollection
import com.nuvio.app.core.anilist.AniListMediaListEntry
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** AniList's `MediaListStatus` values, as the app cares about them. */
internal object AniListListStatus {
    const val CURRENT = "CURRENT"
    const val PLANNING = "PLANNING"
    const val COMPLETED = "COMPLETED"
    const val DROPPED = "DROPPED"
    const val PAUSED = "PAUSED"
    const val REPEATING = "REPEATING"
}

/** AniList's `MediaStatus` value for a still-airing / still-publishing title. */
internal const val ANILIST_MEDIA_STATUS_RELEASING = "RELEASING"

data class AniListListsSnapshot(
    val anime: List<AniListMediaListEntry> = emptyList(),
    val manga: List<AniListMediaListEntry> = emptyList(),
    val loadedAtEpochMs: Long? = null,
) {
    val isEmpty: Boolean get() = anime.isEmpty() && manga.isEmpty()
}

data class AniListListsUiState(
    val snapshot: AniListListsSnapshot = AniListListsSnapshot(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Reads the signed-in user's AniList anime and manga lists.
 *
 * `MediaListCollection` returns every status in one request per media type, so a full refresh is two
 * requests total — which matters against AniList's 30/minute cap. Entries are kept raw here; turning
 * them into library sections is [aniListLibraryProjection]'s job.
 */
object AniListListRepository {
    private val log = Logger.withTag("AniListLists")
    private val refreshMutex = Mutex()
    private val cacheHydrated = atomic(false)

    private val _uiState = MutableStateFlow(AniListListsUiState())
    val uiState: StateFlow<AniListListsUiState> = _uiState.asStateFlow()

    fun snapshot(): AniListListsSnapshot = _uiState.value.snapshot

    fun clearLocalState() {
        // The stored payload is deliberately left alone: this also runs on every cold start, before
        // the AniList credentials have been read back off disk. `AniListListCache.clear()` on an
        // explicit disconnect is what drops it.
        cacheHydrated.value = false
        _uiState.value = AniListListsUiState()
    }

    /**
     * Publishes the previous session's lists from [AniListListCache], so the library rows are there
     * on the first frame instead of after a viewer lookup plus two list requests. Runs at most once
     * per connection, never overwrites lists already in memory, and does nothing when no account is
     * connected.
     */
    suspend fun ensureLoaded() {
        if (!cacheHydrated.compareAndSet(expect = false, update = true)) return
        if (AniListAuthRepository.accessTokenOrNull() == null) return

        val accountId = AniListAuthRepository.snapshot().accountId
        val cached = withContext(Dispatchers.Default) { AniListListCache.load(accountId) } ?: return

        refreshMutex.withLock {
            if (_uiState.value.snapshot.isEmpty) {
                _uiState.value = _uiState.value.copy(snapshot = cached)
            }
        }
    }

    /**
     * Fetches both lists. Returns without touching state when no account is connected, so callers
     * can invoke this freely.
     */
    suspend fun refresh(forceRefresh: Boolean = false) {
        val token = AniListAuthRepository.accessTokenOrNull() ?: return
        // Cached rows first, so a slow, rate-limited or failing refresh leaves something on screen.
        ensureLoaded()
        val userId = AniListAuthRepository.snapshot().accountId ?: run {
            // The viewer lookup is what fills accountId in; without it there is nothing to query.
            AniListAuthRepository.refreshViewer()
            AniListAuthRepository.snapshot().accountId ?: return
        }

        refreshMutex.withLock {
            val previous = _uiState.value.snapshot
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val snapshot = withContext(Dispatchers.Default) {
                    coroutineScope {
                        val animeDeferred = async { entries(userId, "ANIME", token, forceRefresh) }
                        val mangaDeferred = async { entries(userId, "MANGA", token, forceRefresh) }
                        // Continue Watching and every anime library row read the anime list only, so
                        // publish it the moment it lands instead of holding it behind the manga
                        // request. The previous manga entries stay until the new ones arrive.
                        val anime = animeDeferred.await()
                        _uiState.value = _uiState.value.copy(
                            snapshot = previous.copy(
                                anime = anime,
                                loadedAtEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
                            ),
                        )
                        AniListListsSnapshot(
                            anime = anime,
                            manga = mangaDeferred.await(),
                            loadedAtEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
                        )
                    }
                }
                _uiState.value = AniListListsUiState(snapshot = snapshot, isLoading = false)
                withContext(Dispatchers.Default) { AniListListCache.save(snapshot, userId) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "AniList list refresh failed" }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = error.message,
                )
            }
        }
    }

    private suspend fun entries(
        userId: Int,
        mediaType: String,
        token: String,
        forceRefresh: Boolean,
    ): List<AniListMediaListEntry> {
        val query = """
            query {
                MediaListCollection(userId: $userId, type: $mediaType) {
                    lists {
                        name
                        status
                        entries {
                            id
                            status
                            progress
                            progressVolumes
                            score
                            updatedAt
                            media { $ANILIST_LIST_MEDIA_FIELDS }
                        }
                    }
                }
            }
        """.trimIndent()

        val data = AniListClient.query(
            query = query,
            forceRefresh = forceRefresh,
            accessToken = token,
            // This is the user's own progress, and it feeds Continue Watching. Ten minutes of it was
            // enough for an episode marked watched elsewhere — or in this app a moment ago — to keep
            // showing the old position.
            cacheTtlMs = AniListClient.VOLATILE_CACHE_TTL_MS,
        )
        val collectionObject = data["MediaListCollection"] as? JsonObject ?: return emptyList()
        val collection = AniListClient.json.decodeFromJsonElement(
            AniListMediaListCollection.serializer(),
            collectionObject,
        )

        // AniList splits entries into named custom lists, so the same entry can appear more than
        // once; the entry id is the stable identity.
        return collection.lists
            .flatMap { group ->
                group.entries.map { entry ->
                    if (entry.status != null) entry else entry.copy(status = group.status)
                }
            }
            .filter { it.media != null }
            .distinctBy { entry -> entry.id ?: entry.media?.id }
    }
}
