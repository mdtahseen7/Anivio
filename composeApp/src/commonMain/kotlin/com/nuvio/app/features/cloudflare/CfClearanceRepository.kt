package com.nuvio.app.features.cloudflare

import com.nuvio.app.features.downloads.DownloadsClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

data class CfClearanceUiState(
    val enabled: Boolean = false,
    /** Hosts that returned a Cloudflare challenge and still need the user to clear them. */
    val challengeHosts: Set<String> = emptySet(),
)

/**
 * Holds the opt-in Cloudflare-clearance setting and the per-host clearance tokens, and tracks which
 * hosts currently need the user to solve a challenge. Scraping/playback code reads
 * [freshClearanceHeadersFor]; the HTTP layer calls [noteChallengeHost] when it sees a challenge; the
 * WebView solver calls [putClearance] once the user passes it.
 */
object CfClearanceRepository {
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(CfClearanceUiState())
    val uiState: StateFlow<CfClearanceUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var clearances: MutableMap<String, CfClearanceEntry> = mutableMapOf()

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val enabled = CfClearanceStorage.loadEnabled() ?: false
        clearances = runCatching {
            CfClearanceStorage.loadClearancesJson()
                ?.let { json.decodeFromString<Map<String, CfClearanceEntry>>(it) }
                ?.toMutableMap()
        }.getOrNull() ?: mutableMapOf()
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun setEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.enabled == enabled) return
        _uiState.value = _uiState.value.copy(enabled = enabled)
        CfClearanceStorage.saveEnabled(enabled)
    }

    /** The cookie + User-Agent to inject for [url], or null if disabled / no fresh token for its host. */
    fun freshClearanceHeadersFor(url: String): CfClearanceEntry? {
        if (!loaded) ensureLoaded()
        if (!_uiState.value.enabled) return null
        val host = cfHostKey(url).ifEmpty { return null }
        val entry = clearances[host] ?: return null
        val age = DownloadsClock.nowEpochMs() - entry.capturedAtEpochMs
        return if (age in 0 until CF_CLEARANCE_TTL_MS) entry else null
    }

    /** Record that [url]'s host is behind a Cloudflare challenge, so the UI can offer "tap to clear". */
    fun noteChallengeHost(url: String) {
        ensureLoaded()
        if (!_uiState.value.enabled) return
        val host = cfHostKey(url).ifEmpty { return }
        if (host in _uiState.value.challengeHosts) return
        _uiState.value = _uiState.value.copy(challengeHosts = _uiState.value.challengeHosts + host)
    }

    /** Store a freshly captured clearance (from the WebView solver) and clear the host's challenge flag. */
    fun putClearance(host: String, cookie: String, userAgent: String) {
        ensureLoaded()
        val key = cfHostKey(host).ifEmpty { return }
        clearances[key] = CfClearanceEntry(
            cookie = cookie,
            userAgent = userAgent,
            capturedAtEpochMs = DownloadsClock.nowEpochMs(),
        )
        persist()
        _uiState.value = _uiState.value.copy(challengeHosts = _uiState.value.challengeHosts - key)
    }

    fun dismissChallenge(host: String) {
        val key = cfHostKey(host)
        _uiState.value = _uiState.value.copy(challengeHosts = _uiState.value.challengeHosts - key)
    }

    fun clearAll() {
        ensureLoaded()
        clearances.clear()
        persist()
        _uiState.value = _uiState.value.copy(challengeHosts = emptySet())
    }

    private fun persist() {
        runCatching { CfClearanceStorage.saveClearancesJson(json.encodeToString(clearances.toMap())) }
    }
}
