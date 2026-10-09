package com.nuvio.app.features.profile

import com.nuvio.app.core.anilist.AniListUserStatistics
import com.nuvio.app.features.watched.WatchedItem
import com.nuvio.app.features.watched.isEpisode

/**
 * A watch event reduced to just what the stats engine needs. Decoupling from [WatchedItem] keeps
 * [ProfileStatsEngine] a pure function that's trivial to unit test.
 */
data class WatchEvent(
    val epochMs: Long,
    val isEpisode: Boolean,
    val isMovie: Boolean,
    val seriesId: String,
    val seriesName: String,
    val runtimeMinutes: Int?,
    /** Stable watched-item identity. Used to ignore duplicated history rows from sync providers. */
    val eventId: String = "",
)

/**
 * AniList-sourced enrichment the local history can't provide (accurate watch time, genre/studio
 * breadth, completed-series count). All optional — the engine degrades gracefully to local-only.
 */
data class ProfileAniListInput(
    val minutesWatched: Long? = null,
    val episodesWatched: Int? = null,
    val completedSeries: Int? = null,
    val distinctGenres: Int = 0,
    val distinctStudios: Int = 0,
) {
    companion object {
        fun from(stats: AniListUserStatistics?): ProfileAniListInput {
            val anime = stats?.anime ?: return ProfileAniListInput()
            val completed = anime.statuses
                .firstOrNull { it.status.equals("COMPLETED", ignoreCase = true) }
                ?.count ?: 0
            return ProfileAniListInput(
                minutesWatched = anime.minutesWatched.takeIf { it > 0 },
                episodesWatched = anime.episodesWatched.takeIf { it > 0 },
                completedSeries = completed.takeIf { it > 0 },
                distinctGenres = anime.genres.count { !it.genre.isNullOrBlank() },
                distinctStudios = anime.studios.count { it.studio?.name?.isNotBlank() == true },
            )
        }
    }
}

/**
 * The pure stats engine. Given raw watch events (and optional AniList enrichment and "now"), it
 * computes streaks, totals, heatmap, histograms, level, habit type and achievement progress. No
 * platform/date dependencies beyond [ProfileStatsCalendar]; everything else is deterministic.
 */
object ProfileStatsEngine {

    const val HEATMAP_DAYS = 112 // 16 weeks

    fun eventsFrom(items: List<WatchedItem>): List<WatchEvent> =
        items.map { item ->
            val movie = item.type.equals("movie", ignoreCase = true) ||
                item.type.equals("film", ignoreCase = true)
            WatchEvent(
                epochMs = item.markedAtEpochMs,
                isEpisode = item.isEpisode,
                isMovie = movie,
                seriesId = item.id,
                seriesName = item.name,
                runtimeMinutes = null,
                eventId = "${item.type}:${item.id}:${item.season ?: "-"}:${item.episode ?: "-"}",
            )
        }

    fun compute(
        events: List<WatchEvent>,
        aniList: ProfileAniListInput = ProfileAniListInput(),
        nowEpochMs: Long,
        previouslyUnlocked: Map<String, Long> = emptyMap(),
        /** Completion keys from the active history source, independent of AniList enrichment. */
        completedSeriesKeys: Set<String> = emptySet(),
    ): ProfileStats {
        if (events.isEmpty() && aniList.minutesWatched == null && completedSeriesKeys.isEmpty() && previouslyUnlocked.isEmpty()) {
            return ProfileStats.EMPTY
        }

        val decorated = events
            .filter { it.epochMs > 0 }
            .distinctBy(::eventIdentity)
            .map { it to ProfileStatsCalendar.fieldsOf(it.epochMs) }
            .sortedBy { it.first.epochMs }

        val todayEpochDay = ProfileStatsCalendar.todayEpochDay(nowEpochMs)

        // ---- Day aggregation ----
        val perDayCount = sortedMapCounts(decorated.map { it.second.epochDay })
        val activeDays = perDayCount.size
        val maxEpisodesInOneDay = perDayCount.values.maxOrNull() ?: 0

        // ---- Streak ----
        val streak = computeStreak(perDayCount.keys.toList(), todayEpochDay)

        // ---- Heatmap (last HEATMAP_DAYS) ----
        val heatmap = buildHeatmap(perDayCount, todayEpochDay)

        // ---- Histograms ----
        val hourHistogram = IntArray(24)
        val weekdayHistogram = IntArray(7)
        var lateNight = 0
        var earlyMorning = 0
        var weekend = 0
        decorated.forEach { (event, f) ->
            if (!event.isEpisode && !event.isMovie) return@forEach
            hourHistogram[f.hour]++
            weekdayHistogram[f.weekday]++
            if (event.isEpisode) {
                when (f.hour) {
                    in 0..4 -> lateNight++
                    in 5..8 -> earlyMorning++
                }
                if (f.weekday >= 5) weekend++ // Saturday(5)/Sunday(6)
            }
        }

        // ---- Totals ----
        val episodeEvents = decorated.filter { it.first.isEpisode }
        val movieEvents = decorated.filter { it.first.isMovie }
        val localEpisodes = episodeEvents.size
        val localMovies = movieEvents.size

        val episodesWatched = aniList.episodesWatched?.coerceAtLeast(localEpisodes) ?: localEpisodes
        val seriesGroups = episodeEvents.groupBy { it.first.seriesId }
        val seriesTouched = seriesGroups.size
        val longestSeries = seriesGroups.maxByOrNull { it.value.size }
        val longestSeriesEpisodes = longestSeries?.value?.size ?: 0
        val seriesCompleted = maxOf(aniList.completedSeries ?: 0, completedSeriesKeys.size)

        val localMinutes = decorated.sumOf { (event, _) ->
            if (!event.isEpisode && !event.isMovie) return@sumOf 0L
            val fallback = if (event.isMovie) {
                ProfileLevelMath.DEFAULT_MOVIE_MINUTES
            } else {
                ProfileLevelMath.DEFAULT_EPISODE_MINUTES
            }
            (event.runtimeMinutes ?: fallback).coerceAtLeast(0).toLong()
        }
        val minutesWatched = aniList.minutesWatched ?: localMinutes

        val completionRate = if (seriesTouched > 0) {
            (seriesCompleted.toFloat() / seriesTouched.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val totals = ProfileTotals(
            episodesWatched = episodesWatched,
            seriesTouched = seriesTouched,
            seriesCompleted = seriesCompleted,
            moviesWatched = localMovies,
            minutesWatched = minutesWatched,
            activeDays = activeDays,
            completionRate = completionRate,
            longestSeriesEpisodes = longestSeriesEpisodes,
            longestSeriesName = longestSeries?.value?.firstOrNull()?.first?.seriesName,
            episodesPerActiveDay = if (activeDays > 0) localEpisodes.toFloat() / activeDays else 0f,
            firstWatchEpochMs = decorated.firstOrNull()?.first?.epochMs,
        )

        // ---- Habit ----
        val habit = deriveHabit(hourHistogram, weekdayHistogram)

        // ---- Achievements ----
        val baseMetrics = ProfileMetrics(
            episodesWatched = episodesWatched,
            moviesWatched = localMovies,
            seriesCompleted = seriesCompleted,
            currentStreakDays = streak.currentDays,
            longestStreakDays = streak.longestDays,
            activeDays = activeDays,
            maxEpisodesInOneDay = maxEpisodesInOneDay,
            distinctGenres = aniList.distinctGenres,
            distinctStudios = aniList.distinctStudios,
            lateNightEpisodes = lateNight,
            earlyMorningEpisodes = earlyMorning,
            weekendEpisodes = weekend,
            longestSeriesEpisodes = longestSeriesEpisodes,
            completionRatePercent = (completionRate * 100).toInt(),
        )

        // Rewards can unlock a level achievement, which can itself contribute XP. Iterate to the
        // fixed point (the finite catalog makes this deterministic and bounded).
        val watchXp = (minutesWatched.coerceAtLeast(0) * ProfileLevelMath.XP_PER_MINUTE).toLong()
        val episodeXp = localEpisodes.toLong() * ProfileLevelMath.XP_PER_EPISODE_COMPLETION
        val seriesXp = seriesCompleted.toLong() * ProfileLevelMath.XP_PER_SERIES_COMPLETION
        val streakXp = streakMilestoneXp(streak.longestDays)
        val baseXp = profileXpSum(watchXp, episodeXp, seriesXp, streakXp)
        var level = ProfileLevelMath.levelForXp(baseXp)
        var achievements = emptyList<Achievement>()
        repeat(4) {
            achievements = evaluateAchievements(
                metrics = baseMetrics.copy(level = level.level),
                nowEpochMs = nowEpochMs,
                previouslyUnlocked = previouslyUnlocked,
            )
            val total = profileXpSum(baseXp, achievements.filter { it.unlocked }.sumOf { it.xpReward })
            val resolved = ProfileLevelMath.levelForXp(total)
            if (resolved.level == level.level) {
                level = resolved
                return@repeat
            }
            level = resolved
        }
        // Re-evaluate once with the resolved level so level-milestone progress matches display.
        achievements = evaluateAchievements(baseMetrics.copy(level = level.level), nowEpochMs, previouslyUnlocked)
        val achievementXp = achievements.filter { it.unlocked }.sumOf { it.xpReward }
        val breakdown = ProfileXpBreakdown(
            watchMinutes = watchXp,
            episodeCompletions = episodeXp,
            seriesCompletions = seriesXp,
            streakMilestones = streakXp,
            achievementRewards = achievementXp,
        )
        level = ProfileLevelMath.levelForXp(breakdown.total)

        return ProfileStats(
            level = level,
            xpBreakdown = breakdown,
            streak = streak,
            totals = totals,
            heatmap = heatmap,
            hourHistogram = hourHistogram.toList(),
            weekdayHistogram = weekdayHistogram.toList(),
            habit = habit,
            achievements = achievements,
        )
    }

    fun evaluateAchievements(
        metrics: ProfileMetrics,
        nowEpochMs: Long,
        previouslyUnlocked: Map<String, Long>,
    ): List<Achievement> = AchievementCatalog.definitions.map { def ->
        val current = def.measure(metrics).coerceAtLeast(0)
        val priorUnlockedAt = previouslyUnlocked[def.id]
        // Unlock history is append-only. A tracker correction may lower current progress, but it
        // must not revoke an earned badge or its one-time XP reward.
        val unlocked = priorUnlockedAt != null || current >= def.target
        val unlockedAt = when {
            priorUnlockedAt != null -> priorUnlockedAt
            unlocked -> nowEpochMs
            else -> null
        }
        Achievement(
            id = def.id,
            title = def.title,
            description = def.description,
            category = def.category,
            tier = def.tier,
            iconKey = def.iconKey,
            progress = if (def.target <= 0) 1f else (current.toFloat() / def.target).coerceIn(0f, 1f),
            current = current.coerceAtMost(def.target),
            target = def.target,
            unlocked = unlocked,
            unlockedAtEpochMs = unlockedAt,
            xpReward = def.xpReward,
        )
    }

    /**
     * Walk distinct local activity days newest→oldest. A streak is strictly consecutive calendar
     * days and can end today or yesterday, so midnight and duplicate history entries cannot
     * inflate it.
     */
    private fun computeStreak(activeDaysAsc: List<Long>, todayEpochDay: Long): ProfileStreak {
        if (activeDaysAsc.isEmpty()) return ProfileStreak()
        val days = activeDaysAsc.toSortedSet().toList()
        val lastActive = days.last()
        val watchedToday = lastActive == todayEpochDay

        // Longest strict run across all history.
        var longest = 1
        var run = 1
        for (i in 1 until days.size) {
            val gap = days[i] - days[i - 1]
            run = if (gap == 1L) run + 1 else 1
            if (run > longest) longest = run
        }

        // Current streak: only valid if the last active day is today or yesterday.
        val daysSinceLast = todayEpochDay - lastActive
        val current: Int
        if (daysSinceLast > 1L) {
            current = 0
        } else {
            var c = 1
            for (i in days.size - 1 downTo 1) {
                val gap = days[i] - days[i - 1]
                if (gap == 1L) c++ else break
            }
            current = c
        }

        return ProfileStreak(
            currentDays = current,
            longestDays = maxOf(longest, current),
            freezeActive = false,
            lastActiveEpochDay = lastActive,
            watchedToday = watchedToday,
        )
    }

    private fun buildHeatmap(
        perDayCount: Map<Long, Int>,
        todayEpochDay: Long,
    ): List<HeatmapDay> {
        val start = todayEpochDay - (HEATMAP_DAYS - 1)
        val max = perDayCount.values.maxOrNull() ?: 0
        return (start..todayEpochDay).map { day ->
            val count = perDayCount[day] ?: 0
            HeatmapDay(epochDay = day, count = count, intensity = intensityBucket(count, max))
        }
    }

    private fun intensityBucket(count: Int, max: Int): Int {
        if (count <= 0) return 0
        if (max <= 1) return if (count > 0) 4 else 0
        val ratio = count.toFloat() / max.toFloat()
        return when {
            ratio <= 0.25f -> 1
            ratio <= 0.5f -> 2
            ratio <= 0.75f -> 3
            else -> 4
        }
    }

    private fun deriveHabit(hourHistogram: IntArray, weekdayHistogram: IntArray): WatchHabitType {
        val total = hourHistogram.sum()
        if (total < 5) return WatchHabitType.UNKNOWN

        val weekendCount = weekdayHistogram.getOrElse(5) { 0 } + weekdayHistogram.getOrElse(6) { 0 }
        if (weekendCount.toFloat() / total >= 0.6f) return WatchHabitType.WEEKEND_WARRIOR

        val night = (0..4).sumOf { hourHistogram[it] } + hourHistogram[23] + hourHistogram[22]
        val morning = (5..8).sumOf { hourHistogram[it] }
        val afternoon = (9..16).sumOf { hourHistogram[it] }
        val evening = (17..21).sumOf { hourHistogram[it] }
        val max = maxOf(night, morning, afternoon, evening)
        return when (max) {
            night -> WatchHabitType.NIGHT_OWL
            morning -> WatchHabitType.EARLY_BIRD
            afternoon -> WatchHabitType.AFTERNOON_DELIGHT
            else -> WatchHabitType.PRIME_TIME
        }
    }

    private fun sortedMapCounts(days: List<Long>): Map<Long, Int> {
        val map = HashMap<Long, Int>()
        days.forEach { map[it] = (map[it] ?: 0) + 1 }
        return map.toSortedMap()
    }

    private fun eventIdentity(event: WatchEvent): String = event.eventId.ifBlank {
        "${event.seriesId}:${event.isEpisode}:${event.isMovie}:${event.epochMs}"
    }

    private fun streakMilestoneXp(longestStreakDays: Int): Long =
        listOf(3, 7, 14, 30, 60, 100, 365)
            .count { longestStreakDays >= it }
            .toLong() * ProfileLevelMath.XP_PER_STREAK_MILESTONE
}
