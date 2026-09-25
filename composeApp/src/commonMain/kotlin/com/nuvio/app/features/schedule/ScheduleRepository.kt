package com.nuvio.app.features.schedule

import com.nuvio.app.core.anilist.AniListClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Weekly anime airing schedule for the schedule page.
 *
 * Sourced from AniList's `airingSchedules` query — the same upstream data AniChart and LiveChart
 * render, but through the GraphQL API this app already uses, so it inherits the shared rate-limit
 * handling in [AniListClient]. LiveChart and AniChart themselves are browser SPAs with no public
 * JSON API, so they cannot be queried directly.
 */
object ScheduleRepository {
    /** One week on either side of now covers "earlier today" and the next few days. */
    private const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    private const val PAGE_SIZE = 50

    data class UiState(
        val entries: List<ScheduleEntry> = emptyList(),
        val isLoading: Boolean = false,
        val error: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _uiState.value = _uiState.value.copy(isLoading = true)
        requestWeek { entries ->
            _uiState.value = UiState(entries = entries)
        }
    }

    suspend fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = false)
        val entries = fetchWeek().getOrElse {
            _uiState.value = UiState(entries = _uiState.value.entries, isLoading = false, error = true)
            return
        }
        _uiState.value = UiState(entries = entries, isLoading = false)
    }

    private fun requestWeek(onResult: (List<ScheduleEntry>) -> Unit) {
        scope.launch {
            onResult(fetchWeek().getOrElse {
                _uiState.value = UiState(entries = _uiState.value.entries, isLoading = false, error = true)
                return@launch
            })
        }
    }

    private suspend fun fetchWeek(): Result<List<ScheduleEntry>> {
        val nowSec = LibraryScheduleClock.nowEpochMs() / 1000
        val variables = buildJsonObject {
            // Start at local midnight today, not a week ago: fetching the past week only dragged
            // already-aired days into the list, which then rendered as "backward" dates after the
            // upcoming ones. `airingAt_greater` is exclusive, so episodes earlier today still count.
            put("weekStart", ScheduleTime.startOfDayEpochSec(nowSec) - 1)
            put("weekEnd", nowSec + WINDOW_MS / 1000)
        }
        return runCatching {
            ScheduleParsing.parseAiringSchedules(
                AniListClient.query(query = SCHEDULE_QUERY, variables = variables),
            ).sortedBy(ScheduleEntry::airingAtEpochSec)
        }
    }

    private val SCHEDULE_QUERY = """
            query (${"$"}weekStart: Int, ${"$"}weekEnd: Int) {
              Page(perPage: $PAGE_SIZE) {
                airingSchedules(airingAt_greater: ${"$"}weekStart, airingAt_lesser: ${"$"}weekEnd) {
                  airingAt
                  episode
                  media {
                    id
                    type
                    title { userPreferred english romaji native }
                    coverImage { large }
                  }
                }
              }
            }
        """.trimIndent()
}
