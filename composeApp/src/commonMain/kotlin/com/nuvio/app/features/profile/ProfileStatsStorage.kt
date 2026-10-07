package com.nuvio.app.features.profile

/**
 * Persists the small amount of profile-stats state that can't be re-derived from watch history:
 * when each achievement was first unlocked, and the last level the user was shown (so we can fire a
 * level-up celebration exactly once). Everything else is recomputed on the fly. Profile-scoped.
 */
internal expect object ProfileStatsStorage {
    /** JSON map of achievementId -> unlockedAtEpochMs, or null when nothing is stored yet. */
    fun loadUnlockedPayload(): String?
    fun saveUnlockedPayload(payload: String)

    /** The last level we celebrated for this profile (0 when never set). */
    fun loadLastSeenLevel(): Int
    fun saveLastSeenLevel(level: Int)
}
