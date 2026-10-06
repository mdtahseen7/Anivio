package com.nuvio.app.features.cloudflare

import kotlinx.serialization.Serializable

/**
 * User-Agent presented by the in-app WebView while solving a Cloudflare challenge, and replayed with
 * the captured cookie on every subsequent request. A cf_clearance cookie is bound to the exact UA
 * (and IP) that solved it, so the two must always travel together.
 */
const val CF_WEBVIEW_UA: String =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

/** Cloudflare clearance cookies live ~30 min; treat ours as stale a little early. */
const val CF_CLEARANCE_TTL_MS: Long = 25L * 60L * 1000L

/** A captured Cloudflare clearance for one host. */
@Serializable
data class CfClearanceEntry(
    /** Full cookie header value, e.g. "cf_clearance=abc; other=1". */
    val cookie: String,
    /** The exact User-Agent that solved the challenge (must be replayed with [cookie]). */
    val userAgent: String,
    val capturedAtEpochMs: Long,
)

/**
 * Normalizes any URL or host string to a bare lowercase host key (no scheme, port, path, or leading
 * "www."), used as the per-host storage key. Returns "" when nothing host-like can be parsed.
 */
fun cfHostKey(input: String): String {
    var s = input.trim()
    if (s.isEmpty()) return ""
    val schemeIdx = s.indexOf("://")
    if (schemeIdx >= 0) s = s.substring(schemeIdx + 3)
    // strip userinfo
    val at = s.indexOf('@')
    if (at >= 0) s = s.substring(at + 1)
    // cut at first path / query / fragment
    s = s.takeWhile { it != '/' && it != '?' && it != '#' }
    // strip port
    val colon = s.indexOf(':')
    if (colon >= 0) s = s.substring(0, colon)
    s = s.lowercase()
    if (s.startsWith("www.")) s = s.substring(4)
    return s
}

private val CF_CHALLENGE_MARKERS = listOf(
    "just a moment",
    "cf-browser-verification",
    "challenge-platform",
    "cf_chl",
    "/cdn-cgi/challenge",
    "cf-chl-",
    "ddos-guard",
)

/**
 * Heuristic: does this response look like a Cloudflare (or DDoS-Guard) challenge rather than real
 * content? Requires a blocking status plus a body that carries a known challenge marker, so a plain
 * 403 from a normal API is not mistaken for a challenge.
 */
fun isCloudflareChallenge(status: Int, body: String?): Boolean {
    if (status != 403 && status != 503 && status != 429) return false
    val head = body?.take(4000)?.lowercase() ?: return false
    return CF_CHALLENGE_MARKERS.any { head.contains(it) }
}
