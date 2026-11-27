package com.nuvio.app.core.anilist

import com.nuvio.app.core.network.retryAfterDelayMillis
import com.nuvio.app.features.addons.RawHttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val WINDOW_MS = 60_000L
private const val MAX_REQUESTS_PER_WINDOW = 25
private const val REQUEST_SPACING_MS = 500L

internal class AniListRequestThrottle(
    private val nowMs: () -> Long,
    private val loadCooldown: () -> Long = { 0L },
    private val saveCooldown: (Long) -> Unit = {},
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Mutex()
    private val sends = ArrayDeque<Long>()
    private var blockedUntilMs: Long? = null
    private var budget = MAX_REQUESTS_PER_WINDOW
    private val _rateLimitUntilMs = MutableStateFlow(0L)
    val rateLimitUntilMs = _rateLimitUntilMs.asStateFlow()

    // Call while holding a transport permit. Only reserve a slot when it can actually be used.
    suspend fun awaitSendSlot() {
        while (true) {
            val waitMs = lock.withLock {
                val now = nowMs()
                val blockedUntil = blockedUntilMs ?: loadCooldown().also { blockedUntilMs = it }
                if (blockedUntil > now) throw AniListRateLimitedException()

                while (sends.isNotEmpty() && now - sends.first() >= WINDOW_MS) sends.removeFirst()
                val spacingAt = sends.lastOrNull()?.plus(REQUEST_SPACING_MS) ?: now
                val windowAt = if (sends.size >= budget) sends[sends.size - budget] + WINDOW_MS else now
                val wait = maxOf(spacingAt, windowAt) - now
                if (wait <= 0L) sends.addLast(now)
                wait
            }
            if (waitMs <= 0L) return
            pause(waitMs)
        }
    }

    suspend fun record(response: RawHttpResponse) = lock.withLock {
        response.header("X-RateLimit-Limit")?.toIntOrNull()?.takeIf { it > 0 }?.let {
            budget = minOf(it, MAX_REQUESTS_PER_WINDOW)
        }
        val limited = response.status == 429
        val exhausted = response.status in 200..299 &&
            response.header("X-RateLimit-Remaining")?.toIntOrNull() == 0
        if (!limited && !exhausted) return@withLock

        val now = nowMs()
        val until = now + response.aniListCooldownMs(now).coerceAtMost(Long.MAX_VALUE - now)
        val previous = blockedUntilMs ?: loadCooldown()
        val deadline = maxOf(previous, until)
        blockedUntilMs = deadline
        if (deadline > previous) saveCooldown(deadline)
        if (limited || _rateLimitUntilMs.value > now) _rateLimitUntilMs.value = deadline
    }
}

internal fun RawHttpResponse.aniListCooldownMs(nowMs: Long): Long {
    val retryDelay = retryAfterDelayMillis(header("Retry-After"), nowMs, fallbackDelayMs = 0L)
    val resetDelay = header("X-RateLimit-Reset")?.toLongOrNull()
        ?.takeIf { it in 1..Long.MAX_VALUE / 1_000L }
        ?.let { it * 1_000L - nowMs }
        ?: 0L
    return maxOf(retryDelay, resetDelay).takeIf { it > 0L } ?: WINDOW_MS
}

private fun RawHttpResponse.header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.trim()
