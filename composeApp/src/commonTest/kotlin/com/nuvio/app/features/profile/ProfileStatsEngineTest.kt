package com.nuvio.app.features.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileStatsEngineTest {

    // 2026-01-15 12:00:00 UTC as a stable "now".
    private val nowMs = 1_768_478_400_000L
    private val dayMs = 86_400_000L

    private fun episodeAt(epochMs: Long, seriesId: String = "s1", name: String = "Series 1") =
        WatchEvent(
            epochMs = epochMs,
            isEpisode = true,
            isMovie = false,
            seriesId = seriesId,
            seriesName = name,
            runtimeMinutes = null,
        )

    @Test
    fun `empty history yields EMPTY stats`() {
        val stats = ProfileStatsEngine.compute(emptyList(), nowEpochMs = nowMs)
        assertEquals(ProfileStats.EMPTY, stats)
    }

    @Test
    fun `consecutive days build a current streak ending today`() {
        val events = listOf(
            episodeAt(nowMs - 2 * dayMs),
            episodeAt(nowMs - 1 * dayMs),
            episodeAt(nowMs),
        )
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        assertEquals(3, stats.streak.currentDays)
        assertEquals(3, stats.streak.longestDays)
        assertTrue(stats.streak.watchedToday)
        assertFalse(stats.streak.freezeActive)
    }

    @Test
    fun `a missed local calendar day breaks a streak`() {
        // today and two days ago: duplicate emissions cannot bridge an inactive calendar day.
        val events = listOf(
            episodeAt(nowMs - 2 * dayMs),
            episodeAt(nowMs),
        )
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        assertEquals(1, stats.streak.currentDays)
        assertFalse(stats.streak.freezeActive)
    }

    @Test
    fun `a two day gap breaks the current streak`() {
        val events = listOf(episodeAt(nowMs - 3 * dayMs), episodeAt(nowMs))
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        // Last activity is today, but the gap before it is >grace, so streak is just today.
        assertEquals(1, stats.streak.currentDays)
    }

    @Test
    fun `stale history produces no current streak`() {
        val events = listOf(episodeAt(nowMs - 10 * dayMs), episodeAt(nowMs - 9 * dayMs))
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        assertEquals(0, stats.streak.currentDays)
        assertEquals(2, stats.streak.longestDays)
    }

    @Test
    fun `totals count episodes and distinct series`() {
        val events = listOf(
            episodeAt(nowMs, seriesId = "a", name = "A"),
            episodeAt(nowMs - dayMs, seriesId = "a", name = "A"),
            episodeAt(nowMs - dayMs, seriesId = "b", name = "B"),
        )
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        assertEquals(3, stats.totals.episodesWatched)
        assertEquals(2, stats.totals.seriesTouched)
        assertEquals(2, stats.totals.longestSeriesEpisodes)
        assertEquals("A", stats.totals.longestSeriesName)
    }

    @Test
    fun `heatmap always spans the configured window and ends today`() {
        val events = listOf(episodeAt(nowMs))
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        assertEquals(ProfileStatsEngine.HEATMAP_DAYS, stats.heatmap.size)
        val today = ProfileStatsCalendar.todayEpochDay(nowMs)
        assertEquals(today, stats.heatmap.last().epochDay)
        assertTrue(stats.heatmap.last().count >= 1)
    }

    @Test
    fun `first episode achievement unlocks immediately`() {
        val events = listOf(episodeAt(nowMs))
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        val first = stats.achievements.first { it.id == "eps_1" }
        assertTrue(first.unlocked)
        assertEquals(nowMs, first.unlockedAtEpochMs)
    }

    @Test
    fun `previously unlocked timestamp is preserved`() {
        val events = listOf(episodeAt(nowMs))
        val earlier = nowMs - 100 * dayMs
        val stats = ProfileStatsEngine.compute(
            events,
            nowEpochMs = nowMs,
            previouslyUnlocked = mapOf("eps_1" to earlier),
        )
        assertEquals(earlier, stats.achievements.first { it.id == "eps_1" }.unlockedAtEpochMs)
    }

    @Test
    fun `AniList enrichment overrides low local episode counts and minutes`() {
        val events = listOf(episodeAt(nowMs))
        val stats = ProfileStatsEngine.compute(
            events,
            aniList = ProfileAniListInput(
                minutesWatched = 60_000,
                episodesWatched = 2500,
                completedSeries = 120,
                distinctGenres = 15,
            ),
            nowEpochMs = nowMs,
        )
        assertEquals(2500, stats.totals.episodesWatched)
        assertEquals(60_000, stats.totals.minutesWatched)
        assertTrue(stats.level.level > 1)
        assertTrue(stats.achievements.first { it.id == "genres_10" }.unlocked)
    }

    @Test
    fun `level math is monotonic and starts at level 1`() {
        assertEquals(1, ProfileLevelMath.levelForXp(0).level)
        var prev = 0L
        for (lvl in 2..50) {
            val total = ProfileLevelMath.totalXpForLevel(lvl)
            assertTrue(total > prev, "level $lvl total $total should exceed $prev")
            prev = total
        }
    }

    @Test
    fun `xp breakdown is additive and awards every source once`() {
        val stats = ProfileStatsEngine.compute(
            events = listOf(episodeAt(nowMs)),
            nowEpochMs = nowMs,
            completedSeriesKeys = setOf("series-1"),
        )

        assertEquals((24 * ProfileLevelMath.XP_PER_MINUTE).toLong(), stats.xpBreakdown.watchMinutes)
        assertEquals(ProfileLevelMath.XP_PER_EPISODE_COMPLETION, stats.xpBreakdown.episodeCompletions)
        assertEquals(ProfileLevelMath.XP_PER_SERIES_COMPLETION, stats.xpBreakdown.seriesCompletions)
        assertEquals(stats.xpBreakdown.total, stats.level.xp)
        assertTrue(stats.xpBreakdown.achievementRewards > 0)
    }

    @Test
    fun `runtime minutes drive watch xp when they are known`() {
        val stats = ProfileStatsEngine.compute(
            events = listOf(episodeAt(nowMs).copy(runtimeMinutes = 30)),
            nowEpochMs = nowMs,
        )
        assertEquals(30L, stats.totals.minutesWatched)
        assertEquals((30 * ProfileLevelMath.XP_PER_MINUTE).toLong(), stats.xpBreakdown.watchMinutes)
    }

    @Test
    fun `duplicate watch events do not duplicate history or xp`() {
        val event = episodeAt(nowMs)
        val one = ProfileStatsEngine.compute(listOf(event), nowEpochMs = nowMs)
        val repeated = ProfileStatsEngine.compute(listOf(event, event), nowEpochMs = nowMs)

        assertEquals(one.totals.episodesWatched, repeated.totals.episodesWatched)
        assertEquals(one.totals.minutesWatched, repeated.totals.minutesWatched)
        assertEquals(one.level.xp, repeated.level.xp)
        assertEquals(one.streak.currentDays, repeated.streak.currentDays)
    }

    @Test
    fun `historical achievement survives a tracker correction and keeps its one-time reward`() {
        val oldUnlock = nowMs - dayMs
        val corrected = ProfileStatsEngine.compute(
            events = emptyList(),
            aniList = ProfileAniListInput(minutesWatched = 1),
            nowEpochMs = nowMs,
            previouslyUnlocked = mapOf("eps_1" to oldUnlock),
        )
        val achievement = corrected.achievements.first { it.id == "eps_1" }

        assertTrue(achievement.unlocked)
        assertEquals(oldUnlock, achievement.unlockedAtEpochMs)
        assertEquals(achievement.xpReward, corrected.xpBreakdown.achievementRewards)
    }

    @Test
    fun `rank and cosmetics are derived from level`() {
        assertEquals(ProfileRank.ENTHUSIAST, ProfileRank.forLevel(10))
        val level = ProfileLevelMath.levelForXp(ProfileLevelMath.totalXpForLevel(21))
        assertEquals(ProfileRank.OTAKU.title, level.title)
        assertTrue(level.rewards.isNotEmpty())
    }

    @Test
    fun `level boundaries and very large xp remain valid`() {
        val threshold = ProfileLevelMath.totalXpForLevel(10)
        assertEquals(9, ProfileLevelMath.levelForXp(threshold - 1).level)
        assertEquals(10, ProfileLevelMath.levelForXp(threshold).level)
        val enormous = ProfileLevelMath.levelForXp(Long.MAX_VALUE)
        assertTrue(enormous.level >= 1)
        assertTrue(enormous.xpIntoLevel >= 0)
        assertTrue(enormous.xpForNextLevel > 0)

        val importedOverflow = ProfileStatsEngine.compute(
            events = emptyList(),
            aniList = ProfileAniListInput(minutesWatched = Long.MAX_VALUE),
            nowEpochMs = nowMs,
        )
        assertTrue(importedOverflow.level.xp >= 0)
    }

    @Test
    fun `achievement evaluation awards level milestones`() {
        val achievements = ProfileStatsEngine.evaluateAchievements(
            metrics = ProfileMetrics(
                episodesWatched = 0,
                moviesWatched = 0,
                seriesCompleted = 0,
                currentStreakDays = 0,
                longestStreakDays = 0,
                activeDays = 0,
                maxEpisodesInOneDay = 0,
                distinctGenres = 0,
                distinctStudios = 0,
                lateNightEpisodes = 0,
                earlyMorningEpisodes = 0,
                weekendEpisodes = 0,
                longestSeriesEpisodes = 0,
                completionRatePercent = 0,
                level = 25,
            ),
            nowEpochMs = nowMs,
            previouslyUnlocked = emptyMap(),
        )
        assertTrue(achievements.first { it.id == "level_5" }.unlocked)
        assertTrue(achievements.first { it.id == "level_25" }.unlocked)
        assertFalse(achievements.first { it.id == "level_50" }.unlocked)
    }

    @Test
    fun `initial profile calculation never produces a level celebration`() {
        assertFalse(shouldCelebrateLevel(previousLevel = 0, newLevel = 50))
        assertFalse(shouldCelebrateAchievement(isFirstProfileCalculation = true, wasPreviouslyUnlocked = false))
        assertTrue(shouldCelebrateLevel(previousLevel = 4, newLevel = 5))
    }
}
