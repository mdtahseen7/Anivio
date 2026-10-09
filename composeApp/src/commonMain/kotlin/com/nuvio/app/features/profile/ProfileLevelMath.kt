package com.nuvio.app.features.profile

import kotlin.math.sqrt

/**
 * Named rank tiers. The displayed title is chosen from the current level via [ProfileLevelMath];
 * ranks give the level number an anime-flavored identity instead of a bare integer.
 */
enum class ProfileRank(val minLevel: Int, val title: String, val rewards: List<ProfileCosmeticReward>) {
    NEWCOMER(1, "Newcomer", listOf(ProfileCosmeticReward("emblem_newcomer", ProfileCosmeticKind.RANK_EMBLEM, "Newcomer emblem"))),
    INITIATE(3, "Initiate", listOf(ProfileCosmeticReward("frame_initiate", ProfileCosmeticKind.PROFILE_FRAME, "Initiate frame"))),
    FAN(6, "Fan", listOf(ProfileCosmeticReward("title_fan", ProfileCosmeticKind.TITLE, "Anime Fan"))),
    ENTHUSIAST(10, "Enthusiast", listOf(ProfileCosmeticReward("emblem_enthusiast", ProfileCosmeticKind.RANK_EMBLEM, "Enthusiast emblem"))),
    BINGER(15, "Binger", listOf(ProfileCosmeticReward("frame_binger", ProfileCosmeticKind.PROFILE_FRAME, "Binger frame"))),
    OTAKU(21, "Otaku", listOf(ProfileCosmeticReward("title_otaku", ProfileCosmeticKind.TITLE, "Otaku"))),
    SEASONED_OTAKU(28, "Seasoned Otaku", listOf(ProfileCosmeticReward("emblem_seasoned_otaku", ProfileCosmeticKind.RANK_EMBLEM, "Seasoned emblem"))),
    CONNOISSEUR(36, "Connoisseur", listOf(ProfileCosmeticReward("frame_connoisseur", ProfileCosmeticKind.PROFILE_FRAME, "Connoisseur frame"))),
    SENPAI(45, "Senpai", listOf(ProfileCosmeticReward("title_senpai", ProfileCosmeticKind.TITLE, "Senpai"))),
    SAGE(55, "Anime Sage", listOf(ProfileCosmeticReward("emblem_sage", ProfileCosmeticKind.RANK_EMBLEM, "Sage emblem"))),
    LEGEND(70, "Living Legend", listOf(ProfileCosmeticReward("frame_legend", ProfileCosmeticKind.PROFILE_FRAME, "Legend frame"))),
    WEEB_SUPREME(90, "Weeb Supreme", listOf(ProfileCosmeticReward("title_weeb_supreme", ProfileCosmeticKind.TITLE, "Weeb Supreme")));

    companion object {
        fun forLevel(level: Int): ProfileRank =
            entries.lastOrNull { level >= it.minLevel } ?: NEWCOMER
    }
}

/**
 * XP and level math. The per-level cost rises quadratically: early levels arrive in a few
 * episodes, mid-game rewards sustained watching, and late ranks remain aspirational. Kept pure
 * and integer-only so it stays stable on every platform.
 */
object ProfileLevelMath {
    /**
     * XP awarded per minute of watch time. Calibrated so the rank ladder spans a realistic anime
     * lifetime: the top rank (level 90 / Weeb Supreme ≈ 258,000 XP) lands near ~510 days watched,
     * so an avid ~100-day viewer sits mid-ladder instead of pinning to the max rank. This is the
     * main tuning knob — raise it to make levels come faster, lower it to make them rarer.
     */
    const val XP_PER_MINUTE: Double = 0.35
    const val XP_PER_EPISODE_COMPLETION: Long = 12
    const val XP_PER_SERIES_COMPLETION: Long = 180
    const val XP_PER_STREAK_MILESTONE: Long = 75

    /** Fallback runtime (minutes) for a watched episode when no real runtime is known. */
    const val DEFAULT_EPISODE_MINUTES: Int = 24

    /** Fallback runtime (minutes) for a watched movie when no real runtime is known. */
    const val DEFAULT_MOVIE_MINUTES: Int = 110

    // Level 2 takes 80 XP, level 25 ≈ 7.6k XP, level 50 ≈ 47.8k XP, and level 90 ≈ 258k XP.
    // That gives a tangible early cadence without collapsing the prestige endgame.
    private const val BASE: Long = 80L // XP needed to reach level 2
    private const val LINEAR_GROWTH: Long = 5L
    private const val QUADRATIC_GROWTH: Long = 1L
    private const val MAX_LEVEL = 100_000

    /** Cumulative XP required to *reach* [level] (level 1 = 0). */
    fun totalXpForLevel(level: Int): Long {
        if (level <= 1) return 0
        val n = (level.coerceAtMost(MAX_LEVEL) - 2).toLong()
        // Sum [BASE + LINEAR_GROWTH*i + QUADRATIC_GROWTH*i*i], i=0..n.
        return BASE * (n + 1) +
            LINEAR_GROWTH * n * (n + 1) / 2 +
            QUADRATIC_GROWTH * n * (n + 1) * (2 * n + 1) / 6
    }

    /** XP required to go from [level]-1 to [level] (the span of the previous level). */
    fun xpForLevel(level: Int): Long {
        if (level <= 1) return 0
        return totalXpForLevel(level) - totalXpForLevel(level - 1)
    }

    /** Resolve an absolute XP amount into a [ProfileLevel]. */
    fun levelForXp(xp: Long): ProfileLevel {
        val safeXp = xp.coerceAtLeast(0)
        var low = 1
        var high = MAX_LEVEL
        while (low < high) {
            val mid = low + (high - low + 1) / 2
            if (totalXpForLevel(mid) <= safeXp) low = mid else high = mid - 1
        }
        val level = low
        val floor = totalXpForLevel(level)
        val span = xpForLevel(level + 1).coerceAtLeast(1)
        return ProfileLevel(
            level = level,
            title = ProfileRank.forLevel(level).title,
            xp = safeXp,
            xpIntoLevel = (safeXp - floor).coerceAtLeast(0),
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
