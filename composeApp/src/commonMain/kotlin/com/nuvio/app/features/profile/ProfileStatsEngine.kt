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

    /** The grace rule: a streak survives this many consecutive missed days ("freeze"). */
    private const val STREAK_GRACE_DAYS = 1

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
            )
        }

    fun compute(
        events: List<WatchEvent>,
        aniList: ProfileAniListInput = ProfileAniListInput(),
        nowEpochMs: Long,
        previouslyUnlocked: Map<String, Long> = emptyMap(),
    ): ProfileStats {
        if (events.isEmpty() && aniList.minutesWatched == null) {
            return ProfileStats.EMPTY
        }

        val decorated = events
            .filter { it.epochMs > 0 }
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
        val seriesCompleted = aniList.completedSeries ?: 0

        val minutesWatched = aniList.minutesWatched
            ?: (localEpisodes.toLong() * ProfileLevelMath.DEFAULT_EPISODE_MINUTES +
                localMovies.toLong() * ProfileLevelMath.DEFAULT_MOVIE_MINUTES)

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

        // ---- Level ----
        val xp = (minutesWatched * ProfileLevelMath.XP_PER_MINUTE).toLong()
        val level = ProfileLevelMath.levelForXp(xp)

        // ---- Habit ----
        val habit = deriveHabit(hourHistogram, weekdayHistogram)

        // ---- Achievements ----
        val metrics = ProfileMetrics(
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
        val achievements = evaluateAchievements(metrics, nowEpochMs, previouslyUnlocked)

        return ProfileStats(
            level = level,
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
        val unlocked = current >= def.target
        val priorUnlockedAt = previouslyUnlocked[def.id]
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
        )
    }

    /**
     * Walk active days newest→oldest. The current streak counts consecutive active days ending at
     * today (or yesterday), tolerating up to [STREAK_GRACE_DAYS] missed days per gap once.
     */
    private fun computeStreak(activeDaysAsc: List<Long>, todayEpochDay: Long): ProfileStreak {
        if (activeDaysAsc.isEmpty()) return ProfileStreak()
        val days = activeDaysAsc.toSortedSet().toList()
        val lastActive = days.last()
        val watchedToday = lastActive == todayEpochDay

        // Longest streak across all history (grace-tolerant gaps count as continuations once).
        var longest = 1
        var run = 1
        for (i in 1 until days.size) {
            val gap = days[i] - days[i - 1]
            run = when {
                gap == 1L -> run + 1
                gap in 2L..(1L + STREAK_GRACE_DAYS) -> run + 1 // bridged by a freeze
                else -> 1
            }
            if (run > longest) longest = run
        }

        // Current streak: only valid if the last active day is today or within grace of today.
        val daysSinceLast = todayEpochDay - lastActive
        var freezeActive = false
        val current: Int
        if (daysSinceLast > 1L + STREAK_GRACE_DAYS) {
            current = 0
        } else {
            if (daysSinceLast in 1..(1L + STREAK_GRACE_DAYS) && !watchedToday) freezeActive = true
            var c = 1
            for (i in days.size - 1 downTo 1) {
                val gap = days[i] - days[i - 1]
                when {
                    gap == 1L -> c++
                    gap in 2L..(1L + STREAK_GRACE_DAYS) -> { c++; freezeActive = true }
                    else -> break
                }
            }
            current = c
        }

        return ProfileStreak(
            currentDays = current,
            longestDays = maxOf(longest, current),
            freezeActive = freezeActive,
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
}
