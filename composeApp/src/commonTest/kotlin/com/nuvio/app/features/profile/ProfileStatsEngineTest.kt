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
    fun `a single missed day is bridged by the freeze grace rule`() {
        // today, and 2 days ago (yesterday missed) -> grace keeps the streak alive.
        val events = listOf(
            episodeAt(nowMs - 2 * dayMs),
            episodeAt(nowMs),
        )
        val stats = ProfileStatsEngine.compute(events, nowEpochMs = nowMs)
        assertEquals(2, stats.streak.currentDays)
        assertTrue(stats.streak.freezeActive)
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
}
