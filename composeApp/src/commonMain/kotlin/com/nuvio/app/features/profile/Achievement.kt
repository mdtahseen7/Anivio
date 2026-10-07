package com.nuvio.app.features.profile

/** Visual/semantic grouping for the achievements grid. */
enum class AchievementCategory(val label: String) {
    MILESTONE("Milestones"),
    STREAK("Streaks"),
    HABIT("Habits"),
    TASTE("Taste"),
}

/** Bronze/Silver/Gold tiers for achievements that scale; [NONE] for one-off badges. */
enum class AchievementTier { NONE, BRONZE, SILVER, GOLD }

/**
 * A single achievement with its live progress. [progress] is 0..1 and [unlocked] is true once the
 * threshold is met. [unlockedAtEpochMs] is persisted so we can show "unlocked on" and detect
 * freshly-earned badges for the celebration popup.
 */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val tier: AchievementTier,
    val iconKey: String,
    val progress: Float,
    val current: Int,
    val target: Int,
    val unlocked: Boolean,
    val unlockedAtEpochMs: Long? = null,
)

/**
 * Static definition of an achievement: how to measure it against a [ProfileMetrics] snapshot. The
 * engine evaluates every definition to produce live [Achievement]s. Pure + data-driven so new
 * badges are a one-line addition and everything stays unit-testable.
 */
data class AchievementDefinition(
    val id: String,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val tier: AchievementTier,
    val iconKey: String,
    val target: Int,
    val measure: (ProfileMetrics) -> Int,
)

/**
 * The raw numbers achievements are measured against. A thin, flat snapshot so definitions stay
 * declarative and the whole catalog can be evaluated in one pass.
 */
data class ProfileMetrics(
    val episodesWatched: Int,
    val moviesWatched: Int,
    val seriesCompleted: Int,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val activeDays: Int,
    val maxEpisodesInOneDay: Int,
    val distinctGenres: Int,
    val distinctStudios: Int,
    val lateNightEpisodes: Int,   // episodes watched between 00:00–04:59
    val earlyMorningEpisodes: Int, // episodes watched between 05:00–08:59
    val weekendEpisodes: Int,
    val longestSeriesEpisodes: Int,
    val completionRatePercent: Int,
)

/**
 * The curated achievement catalog. Covers milestones, streaks, habits and taste; tiered badges use
 * Bronze/Silver/Gold so there's always a next goal to chase.
 */
object AchievementCatalog {
    val definitions: List<AchievementDefinition> = buildList {
        // ---- Milestones: episodes ----
        add(def("eps_1", "First Steps", "Watch your first episode", AchievementCategory.MILESTONE, AchievementTier.NONE, "play", 1) { it.episodesWatched })
        add(def("eps_100", "Centurion", "Watch 100 episodes", AchievementCategory.MILESTONE, AchievementTier.BRONZE, "episodes", 100) { it.episodesWatched })
        add(def("eps_500", "Devotee", "Watch 500 episodes", AchievementCategory.MILESTONE, AchievementTier.SILVER, "episodes", 500) { it.episodesWatched })
        add(def("eps_1000", "Four Digits", "Watch 1,000 episodes", AchievementCategory.MILESTONE, AchievementTier.GOLD, "episodes", 1000) { it.episodesWatched })

        // ---- Milestones: series completed ----
        add(def("series_1", "The Finisher", "Complete your first series", AchievementCategory.MILESTONE, AchievementTier.NONE, "check", 1) { it.seriesCompleted })
        add(def("series_10", "Collector", "Complete 10 series", AchievementCategory.MILESTONE, AchievementTier.BRONZE, "check", 10) { it.seriesCompleted })
        add(def("series_50", "Archivist", "Complete 50 series", AchievementCategory.MILESTONE, AchievementTier.SILVER, "check", 50) { it.seriesCompleted })
        add(def("series_100", "Hall of Fame", "Complete 100 series", AchievementCategory.MILESTONE, AchievementTier.GOLD, "check", 100) { it.seriesCompleted })

        // ---- Milestones: movies ----
        add(def("movies_10", "Cinephile", "Watch 10 movies", AchievementCategory.MILESTONE, AchievementTier.BRONZE, "movie", 10) { it.moviesWatched })
        add(def("movies_50", "Film Buff", "Watch 50 movies", AchievementCategory.MILESTONE, AchievementTier.SILVER, "movie", 50) { it.moviesWatched })

        // ---- Milestones: epic series ----
        add(def("long_series", "The Long Haul", "Finish a 100+ episode series", AchievementCategory.MILESTONE, AchievementTier.GOLD, "mountain", 100) { it.longestSeriesEpisodes })

        // ---- Streaks ----
        add(def("streak_7", "Warming Up", "Reach a 7-day watch streak", AchievementCategory.STREAK, AchievementTier.BRONZE, "flame", 7) { it.longestStreakDays })
        add(def("streak_30", "On Fire", "Reach a 30-day watch streak", AchievementCategory.STREAK, AchievementTier.SILVER, "flame", 30) { it.longestStreakDays })
        add(def("streak_100", "Unstoppable", "Reach a 100-day watch streak", AchievementCategory.STREAK, AchievementTier.GOLD, "flame", 100) { it.longestStreakDays })
        add(def("active_365", "Year-Round", "Watch on 365 different days", AchievementCategory.STREAK, AchievementTier.GOLD, "calendar", 365) { it.activeDays })

        // ---- Habits ----
        add(def("marathon", "Marathoner", "Watch 10 episodes in a single day", AchievementCategory.HABIT, AchievementTier.SILVER, "run", 10) { it.maxEpisodesInOneDay })
        add(def("binge_20", "Binge Master", "Watch 20 episodes in a single day", AchievementCategory.HABIT, AchievementTier.GOLD, "run", 20) { it.maxEpisodesInOneDay })
        add(def("night_owl", "Night Owl", "Watch 50 episodes after midnight", AchievementCategory.HABIT, AchievementTier.SILVER, "moon", 50) { it.lateNightEpisodes })
        add(def("early_bird", "Early Bird", "Watch 50 episodes in the morning", AchievementCategory.HABIT, AchievementTier.SILVER, "sunrise", 50) { it.earlyMorningEpisodes })
        add(def("weekend", "Weekend Warrior", "Watch 100 episodes on weekends", AchievementCategory.HABIT, AchievementTier.BRONZE, "weekend", 100) { it.weekendEpisodes })

        // ---- Taste ----
        add(def("genres_10", "Genre Explorer", "Watch across 10 different genres", AchievementCategory.TASTE, AchievementTier.BRONZE, "compass", 10) { it.distinctGenres })
        add(def("genres_20", "Omnivore", "Watch across 20 different genres", AchievementCategory.TASTE, AchievementTier.GOLD, "compass", 20) { it.distinctGenres })
        add(def("studios_10", "Studio Scout", "Watch titles from 10 different studios", AchievementCategory.TASTE, AchievementTier.BRONZE, "building", 10) { it.distinctStudios })
        add(def("studios_25", "Studio Loyalist", "Watch titles from 25 different studios", AchievementCategory.TASTE, AchievementTier.SILVER, "building", 25) { it.distinctStudios })
        add(def("completionist", "Completionist", "Keep a 90%+ completion rate (20+ series)", AchievementCategory.TASTE, AchievementTier.GOLD, "trophy", 90) {
            if (it.seriesCompleted >= 20) it.completionRatePercent else 0
        })
    }

    private fun def(
        id: String,
        title: String,
        description: String,
        category: AchievementCategory,
        tier: AchievementTier,
        iconKey: String,
        target: Int,
        measure: (ProfileMetrics) -> Int,
    ) = AchievementDefinition(id, title, description, category, tier, iconKey, target, measure)
}
