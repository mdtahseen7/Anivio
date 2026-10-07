package com.nuvio.app.features.profile

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.anilist.AniListStatisticsRepository
import com.nuvio.app.features.watched.WatchedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** A one-shot celebration the UI should present then clear. */
sealed interface ProfileCelebration {
    data class LevelUp(val newLevel: Int, val title: String) : ProfileCelebration
    data class AchievementUnlocked(val achievement: Achievement) : ProfileCelebration
}

data class ProfileStatsUiState(
    val stats: ProfileStats = ProfileStats.EMPTY,
    val isReady: Boolean = false,
    val aniListConnected: Boolean = false,
    val pendingCelebrations: List<ProfileCelebration> = emptyList(),
)

/**
 * Single source of truth for the Profile screen. It observes local watch history and AniList
 * statistics, recomputes [ProfileStats] on every change via the pure [ProfileStatsEngine], persists
 * achievement-unlock timestamps + last-seen level, and surfaces one-shot level-up / badge-unlock
 * celebrations.
 */
object ProfileStatsRepository {
    private val log = Logger.withTag("ProfileStats")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val unlockedSerializer = MapSerializer(String.serializer(), Long.serializer())

    private val _uiState = MutableStateFlow(ProfileStatsUiState())
    val uiState: StateFlow<ProfileStatsUiState> = _uiState.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                WatchedRepository.uiState,
                AniListStatisticsRepository.uiState,
            ) { watched, aniListStats ->
                watched to aniListStats
            }.collect { (watched, aniListStats) ->
                if (!watched.isLoaded) return@collect
                recompute(
                    watchedItems = watched.items,
                    aniList = ProfileAniListInput.from(aniListStats.statistics),
                    aniListConnected = aniListStats.statistics != null,
                )
            }
        }
    }

    /** Reload persisted unlock state when the active profile changes. */
    fun onProfileChanged() {
        // Next recompute picks up the new profile-scoped storage automatically.
        scope.launch {
            val current = _uiState.value
            recompute(
                watchedItems = WatchedRepository.uiState.value.items,
                aniList = ProfileAniListInput.from(AniListStatisticsRepository.uiState.value.statistics),
                aniListConnected = current.aniListConnected,
            )
        }
    }

    fun consumeCelebration(celebration: ProfileCelebration) {
        _uiState.value = _uiState.value.copy(
            pendingCelebrations = _uiState.value.pendingCelebrations.filterNot { it == celebration },
        )
    }

    private suspend fun recompute(
        watchedItems: List<com.nuvio.app.features.watched.WatchedItem>,
        aniList: ProfileAniListInput,
        aniListConnected: Boolean,
    ) {
        val now = EpisodeReleaseDatePlatform.nowEpochMs()
        val previouslyUnlocked = loadUnlocked()
        val previousLevel = ProfileStatsStorage.loadLastSeenLevel()

        val stats = withContext(Dispatchers.Default) {
            val events = ProfileStatsEngine.eventsFrom(watchedItems)
            ProfileStatsEngine.compute(
                events = events,
                aniList = aniList,
                nowEpochMs = now,
                previouslyUnlocked = previouslyUnlocked,
            )
        }

        // Detect newly-unlocked achievements (unlocked now, absent from the persisted set).
        val celebrations = mutableListOf<ProfileCelebration>()
        val freshlyUnlocked = stats.achievements.filter { it.unlocked && !previouslyUnlocked.containsKey(it.id) }
        if (previouslyUnlocked.isNotEmpty() || !firstEverRun(previouslyUnlocked, stats)) {
            freshlyUnlocked.forEach { celebrations += ProfileCelebration.AchievementUnlocked(it) }
        }

        // Persist the full unlocked map (preserving earliest timestamps).
        val mergedUnlocked = HashMap(previouslyUnlocked)
        stats.achievements.filter { it.unlocked }.forEach { ach ->
            if (!mergedUnlocked.containsKey(ach.id)) {
                mergedUnlocked[ach.id] = ach.unlockedAtEpochMs ?: now
            }
        }
        if (mergedUnlocked != previouslyUnlocked) saveUnlocked(mergedUnlocked)

        // Level-up celebration (skip the very first computation so we don't fire on install).
        if (previousLevel in 1 until stats.level.level) {
            celebrations += ProfileCelebration.LevelUp(stats.level.level, stats.level.title)
        }
        if (stats.level.level != previousLevel) {
            ProfileStatsStorage.saveLastSeenLevel(stats.level.level)
        }

        _uiState.value = ProfileStatsUiState(
            stats = stats,
            isReady = true,
            aniListConnected = aniListConnected,
            pendingCelebrations = _uiState.value.pendingCelebrations + celebrations,
        )
    }

    /** Treat the first-ever run (no persisted unlocks, no last level) as a silent baseline. */
    private fun firstEverRun(previouslyUnlocked: Map<String, Long>, stats: ProfileStats): Boolean =
        previouslyUnlocked.isEmpty() && ProfileStatsStorage.loadLastSeenLevel() == 0

    private fun loadUnlocked(): Map<String, Long> {
        val payload = ProfileStatsStorage.loadUnlockedPayload() ?: return emptyMap()
        return runCatching { json.decodeFromString(unlockedSerializer, payload) }
            .onFailure { log.w(it) { "Failed to decode unlocked achievements" } }
            .getOrDefault(emptyMap())
    }

    private fun saveUnlocked(map: Map<String, Long>) {
        runCatching { ProfileStatsStorage.saveUnlockedPayload(json.encodeToString(unlockedSerializer, map)) }
            .onFailure { log.w(it) { "Failed to persist unlocked achievements" } }
    }
}
