package com.nuvio.app.core.anilist

import com.nuvio.app.features.addons.RawHttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AniListRequestThrottleTest {
    @Test
    fun honoursBothResetHeadersAcrossClientRestarts() = runTest {
        var time = 1_700_000_000_000L
        var savedUntil = 0L
        fun throttle() = AniListRequestThrottle(
            nowMs = { time },
            loadCooldown = { savedUntil },
            saveCooldown = { savedUntil = it },
        )
        val first = throttle()
        first.awaitSendSlot()
        first.record(response(429, "Retry-After" to "30", "X-RateLimit-Reset" to "1700000060"))
        assertEquals(time + 60_000L, savedUntil)
        assertEquals(savedUntil, first.rateLimitUntilMs.value)

        val restarted = throttle()
        time += 15_000L
        assertFailsWith<AniListRateLimitedException> { restarted.awaitSendSlot() }
        assertEquals(0L, restarted.rateLimitUntilMs.value)
        time = savedUntil
        restarted.awaitSendSlot()
    }

    @Test
    fun requestWaitingForSpacingRechecksNewCooldown() = runTest {
        var time = 1_700_000_000_000L
        lateinit var throttle: AniListRequestThrottle
        throttle = AniListRequestThrottle(
            nowMs = { time },
            pause = { wait ->
                time += wait
                throttle.record(response(429))
            },
        )
        throttle.awaitSendSlot()
        assertFailsWith<AniListRateLimitedException> { throttle.awaitSendSlot() }
        assertEquals(time + 60_000L, throttle.rateLimitUntilMs.value)
    }

    @Test
    fun zeroRemainingPausesTrafficWithoutAnErrorBanner() = runTest {
        var time = 1_700_000_000_000L
        val throttle = AniListRequestThrottle(nowMs = { time })
        throttle.awaitSendSlot()
        throttle.record(response(200, "x-ratelimit-remaining" to "0", "x-ratelimit-reset" to "1700000010"))
        assertFailsWith<AniListRateLimitedException> { throttle.awaitSendSlot() }
        assertEquals(0L, throttle.rateLimitUntilMs.value)
        time += 10_000L
        throttle.awaitSendSlot()
    }

    @Test
    fun advertisedLowerBudgetAppliesToTheRollingWindow() = runTest {
        var time = 1_700_000_000_000L
        val start = time
        val throttle = AniListRequestThrottle(nowMs = { time }, pause = { time += it })
        throttle.awaitSendSlot()
        throttle.record(response(200, "X-RateLimit-Limit" to "2"))
        throttle.awaitSendSlot()
        assertEquals(start + 500L, time)
        throttle.awaitSendSlot()
        assertEquals(start + 60_000L, time)
    }

    private fun response(status: Int, vararg headers: Pair<String, String>) =
        RawHttpResponse(status, "", "https://graphql.anilist.co", "", mapOf(*headers))
}
