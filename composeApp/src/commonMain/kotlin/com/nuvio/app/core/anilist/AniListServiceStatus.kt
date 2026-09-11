package com.nuvio.app.core.anilist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Raised when AniList is refusing to serve data at all, as opposed to a query being wrong.
 *
 * Kept separate from the generic failures so the UI can say "AniList is down" instead of leaking a
 * status code: every catalog row, the Discover grid and search all go through the same API, so when
 * this happens the whole app looks broken for a reason that is nobody's fault locally.
 */
class AniListUnavailableException(
    val statusCode: Int? = null,
    val serverMessage: String? = null,
) : IllegalStateException(serverMessage ?: "AniList is currently unavailable")

/**
 * Raised when AniList rate limited this specific request rather than going down.
 *
 * Deliberately *not* an [AniListUnavailableException]: the router must fail this one call and leave
 * the provider alone. Treating throttling as an outage swung every row in the app to the fallback
 * provider for the whole probe window, which is far more disruptive than one empty request.
 */
class AniListRateLimitedException(
    serverMessage: String? = null,
) : IllegalStateException(serverMessage ?: "AniList is rate limiting requests")

/**
 * Whether AniList last answered with an outage.
 *
 * Process-wide rather than per-screen because the outage is a property of the remote service, and
 * home, Discover and search would otherwise each have to discover it independently. Any successful
 * response clears it, so recovery needs no explicit retry from the user.
 */
object AniListServiceStatus {
    private val _isUnavailable = MutableStateFlow(false)
    val isUnavailable: StateFlow<Boolean> = _isUnavailable.asStateFlow()

    /** Set when AniList answers 401/403, reports its API as disabled, or stays down through retries. */
    internal fun reportUnavailable() {
        _isUnavailable.value = true
    }

    /** Cleared as soon as any request gets a usable response back. */
    internal fun reportReachable() {
        _isUnavailable.value = false
    }
}

/**
 * AniList has historically answered outages with a 200 plus a GraphQL error rather than a status
 * code, so the message text is the only signal available.
 */
internal fun String?.indicatesAniListOutage(): Boolean {
    val message = this?.lowercase() ?: return false
    return message.contains("disabled") ||
        message.contains("maintenance") ||
        message.contains("temporarily unavailable") ||
        message.contains("unauthorized") ||
        message.contains("forbidden")
}
