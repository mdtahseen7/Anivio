package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.ANILIST_USER_STATISTICS_FIELDS
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.anilist.AniListUserStatistics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

data class AniListStatisticsUiState(
    val statistics: AniListUserStatistics? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Fetches the signed-in user's aggregate anime statistics from AniList's `User.statistics`. One
 * viewer-scoped query per refresh — AniList computes the breakdowns server-side, so nothing here is
 * derived from the raw list. Cached like every other AniList read.
 */
object AniListStatisticsRepository {
    private val log = Logger.withTag("AniListStats")
    private val refreshMutex = Mutex()

    private val _uiState = MutableStateFlow(AniListStatisticsUiState())
    val uiState: StateFlow<AniListStatisticsUiState> = _uiState.asStateFlow()

    fun clearLocalState() {
        _uiState.value = AniListStatisticsUiState()
    }

    suspend fun refresh(forceRefresh: Boolean = false) {
        val token = AniListAuthRepository.accessTokenOrNull() ?: return
        val userId = AniListAuthRepository.snapshot().accountId ?: run {
            AniListAuthRepository.refreshViewer()
            AniListAuthRepository.snapshot().accountId ?: return
        }

        refreshMutex.withLock {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val query = """
                    query {
                        User(id: $userId) {
                            statistics { $ANILIST_USER_STATISTICS_FIELDS }
                        }
                    }
                """.trimIndent()
                val stats = withContext(Dispatchers.Default) {
                    val data = AniListClient.query(
                        query = query,
                        forceRefresh = forceRefresh,
                        accessToken = token,
                    )
                    val statsObject = (data["User"] as? JsonObject)
                        ?.get("statistics") as? JsonObject
                        ?: return@withContext null
                    AniListClient.json.decodeFromJsonElement(
                        AniListUserStatistics.serializer(),
                        statsObject,
                    )
                }
                _uiState.value = AniListStatisticsUiState(statistics = stats, isLoading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "AniList statistics refresh failed" }
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = error.message)
            }
        }
    }
}
