package com.nuvio.app.features.profile

import kotlin.math.sqrt

/**
 * Named rank tiers. The displayed title is chosen from the current level via [ProfileLevelMath];
 * ranks give the level number an anime-flavored identity instead of a bare integer.
 */
enum class ProfileRank(val minLevel: Int, val title: String) {
    NEWCOMER(1, "Newcomer"),
    INITIATE(3, "Initiate"),
    FAN(6, "Fan"),
    ENTHUSIAST(10, "Enthusiast"),
    BINGER(15, "Binger"),
    OTAKU(21, "Otaku"),
    SEASONED_OTAKU(28, "Seasoned Otaku"),
    CONNOISSEUR(36, "Connoisseur"),
    SENPAI(45, "Senpai"),
    SAGE(55, "Anime Sage"),
    LEGEND(70, "Living Legend"),
    WEEB_SUPREME(90, "Weeb Supreme");

    companion object {
        fun forLevel(level: Int): ProfileRank =
            entries.lastOrNull { level >= it.minLevel } ?: NEWCOMER
    }
}

/**
 * XP and level math. XP is earned from watch time; the curve is quadratic so later levels take
 * progressively longer, keeping high levels meaningful. Kept pure for unit testing.
 */
object ProfileLevelMath {
    /** XP awarded per minute of watch time. One ~24-min episode ≈ 24 XP. */
    const val XP_PER_MINUTE: Double = 1.0

    /** Fallback runtime (minutes) for a watched episode when no real runtime is known. */
    const val DEFAULT_EPISODE_MINUTES: Int = 24

    /** Fallback runtime (minutes) for a watched movie when no real runtime is known. */
    const val DEFAULT_MOVIE_MINUTES: Int = 110

    private const val BASE: Double = 120.0 // XP needed to reach level 2

    /** Cumulative XP required to *reach* [level] (level 1 = 0). */
    fun totalXpForLevel(level: Int): Long {
        if (level <= 1) return 0
        val n = (level - 1).toDouble()
        // Sum of a linear-growth cost: BASE * (n + growth * n(n-1)/2).
        val growth = 0.5
        return (BASE * (n + growth * n * (n - 1) / 2.0)).toLong()
    }

    /** XP required to go from [level]-1 to [level] (the span of the previous level). */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0
        return totalXpForLevel(level) - totalXpForLevel(level - 1)
    }

    /** Resolve an absolute XP amount into a [ProfileLevel]. */
    fun levelForXp(xp: Long): ProfileLevel {
        var level = 1
        while (totalXpForLevel(level + 1) <= xp && level < 999) {
            level++
        }
        val floor = totalXpForLevel(level)
        val span = xpForLevel(level + 1).coerceAtLeast(1)
        return ProfileLevel(
            level = level,
            title = ProfileRank.forLevel(level).title,
            xp = xp,
            xpIntoLevel = xp - floor,
            xpForNextLevel = span,
        )
    }
}

/** Standard-deviation helper reused by analytics panels. */
internal fun List<Double>.populationStdDev(): Double {
    if (isEmpty()) return 0.0
    val mean = average()
    val variance = sumOf { (it - mean) * (it - mean) } / size
    return sqrt(variance)
}
