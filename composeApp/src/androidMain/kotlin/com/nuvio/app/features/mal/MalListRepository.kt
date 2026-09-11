package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.core.mal.MalAnimeListEntry
import com.nuvio.app.core.mal.MalClient
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object MalListStatusValue {
    const val WATCHING = "watching"
    const val COMPLETED = "completed"
    const val ON_HOLD = "on_hold"
    const val DROPPED = "dropped"
    const val PLAN_TO_WATCH = "plan_to_watch"
}

data class MalListsSnapshot(
    val entries: List<MalAnimeListEntry> = emptyList(),
    val loadedAtEpochMs: Long? = null,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

data class MalListsUiState(
    val snapshot: MalListsSnapshot = MalListsSnapshot(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

object MalListRepository {
    private val log = Logger.withTag("MalLists")
    private val refreshMutex = Mutex()
    private val cacheHydrated = atomic(false)
    private val _uiState = MutableStateFlow(MalListsUiState())
    val uiState: StateFlow<MalListsUiState> = _uiState.asStateFlow()

    fun snapshot(): MalListsSnapshot = _uiState.value.snapshot

    fun clearLocalState() {
        cacheHydrated.value = false
        _uiState.value = MalListsUiState()
    }

    suspend fun ensureLoaded() {
        if (!cacheHydrated.compareAndSet(expect = false, update = true)) return
        if (MalAuthRepository.accessTokenOrNull() == null) return
        val accountId = MalAuthRepository.snapshot().accountId ?: return
        val profileId = ProfileRepository.activeProfileId
        val cached = withContext(Dispatchers.Default) { MalListCache.load(profileId, accountId) } ?: return
        refreshMutex.withLock {
            if (_uiState.value.snapshot.isEmpty && profileId == ProfileRepository.activeProfileId &&
                MalAuthRepository.snapshot().accountId == accountId
            ) _uiState.value = _uiState.value.copy(snapshot = cached)
        }
    }

    suspend fun refresh(forceRefresh: Boolean = false) {
        val profileId = ProfileRepository.activeProfileId
        val token = MalAuthRepository.accessTokenOrNull() ?: return
        ensureLoaded()
        val accountId = MalAuthRepository.snapshot().accountId ?: run {
            MalAuthRepository.refreshViewer()
            MalAuthRepository.snapshot().accountId ?: return
        }
        refreshMutex.withLock {
            if (!forceRefresh && isFresh(_uiState.value.snapshot.loadedAtEpochMs)) return
            val previous = _uiState.value.snapshot
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val entries = withContext(Dispatchers.Default) { fetchAllPages(token) }
                if (profileId != ProfileRepository.activeProfileId ||
                    MalAuthRepository.snapshot().accountId != accountId
                ) return
                val snapshot = MalListsSnapshot(
                    entries = entries.distinctBy { it.node.id },
                    loadedAtEpochMs = EpisodeReleaseDatePlatform.nowEpochMs(),
                )
                _uiState.value = MalListsUiState(snapshot = snapshot)
                withContext(Dispatchers.Default) { MalListCache.save(profileId, snapshot, accountId) }
            } catch (error: CancellationException) {
                _uiState.value = MalListsUiState(snapshot = previous)
                throw error
            } catch (error: Throwable) {
                log.w(error) { "MAL list refresh failed" }
                _uiState.value = MalListsUiState(
                    snapshot = previous,
                    errorMessage = error.message ?: "MyAnimeList refresh failed",
                )
            }
        }
    }

    private suspend fun fetchAllPages(token: String): List<MalAnimeListEntry> {
        val result = mutableListOf<MalAnimeListEntry>()
        var offset = 0
        do {
            val page = MalClient.getAnimeListPage(
                accessToken = token,
                limit = MalClient.MAX_PAGE_LIMIT,
                offset = offset,
            )
            result += page.data
            offset += page.data.size
            val hasNext = page.paging.next != null && page.data.isNotEmpty()
        } while (hasNext)
        return result
    }

    private fun isFresh(loadedAtEpochMs: Long?): Boolean = loadedAtEpochMs != null &&
        EpisodeReleaseDatePlatform.nowEpochMs() - loadedAtEpochMs in 0 until FRESHNESS_MS

    private const val FRESHNESS_MS = 5 * 60 * 1000L
}
