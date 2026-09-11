package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.core.mal.MalApiException
import com.nuvio.app.core.mal.MalClient
import com.nuvio.app.core.mal.MalTokenResponse
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Android MAL authorization-code flow using plain PKCE and no embedded client secret. */
object MalAuthRepository : TrackingAuthProvider, MalAuthController {
    private const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
    private const val CALLBACK_HOST = "auth"
    private const val CALLBACK_PATH = "mal"

    private val log = Logger.withTag("MalAuth")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(MalAuthUiState())
    override val uiState: StateFlow<MalAuthUiState> = _uiState.asStateFlow()
    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.MAL,
        displayName = "MyAnimeList",
        capabilities = setOf(
            TrackingCapability.AUTHENTICATION,
            TrackingCapability.LIBRARY_READ,
            TrackingCapability.LIBRARY_WRITE,
            TrackingCapability.WATCHED_READ,
            TrackingCapability.WATCHED_WRITE,
            TrackingCapability.PROGRESS_READ,
            TrackingCapability.PROGRESS_WRITE,
            TrackingCapability.SCROBBLE,
        ),
    )

    private var hasLoaded = false
    private var loadedProfileId: Int? = null
    private var stored = MalStoredAuthState()
    private var tokens: MalStoredTokens? = null
    private val tokenLock = Any()
    private val refreshMutex = Mutex()

    init {
        TrackingProviderRegistry.register(this)
    }

    fun snapshot(): MalAuthUiState {
        ensureLoaded()
        return uiState.value
    }

    override fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (hasLoaded && loadedProfileId == profileId) return
        loadFromDisk(profileId)
    }

    override fun onProfileChanged() {
        MalListRepository.clearLocalState()
        loadFromDisk(ProfileRepository.activeProfileId)
    }

    override fun clearLocalState() {
        MalListRepository.clearLocalState()
        hasLoaded = false
        loadedProfileId = null
        stored = MalStoredAuthState()
        synchronized(tokenLock) { tokens = null }
        publish()
    }

    override fun removeStoredProfile(profileId: Int) {
        MalAuthStorage.removeProfile(profileId)
        MalListCache.clear(profileId)
    }

    override fun onConnectRequested(): String? {
        ensureLoaded()
        if (!MalConfig.isConfigured) {
            publish(error = MalAuthError.MISSING_CLIENT_ID)
            return null
        }
        val state = MalAuthRandom.state()
        val verifier = MalAuthRandom.codeVerifier()
        stored = stored.copy(
            pendingState = state,
            pendingCodeVerifier = verifier,
            pendingStartedAtEpochMs = nowMs(),
        )
        persistMetadata()
        publish(error = null)
        return authorizationUrl(state, verifier)
    }

    override fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = stored.pendingState?.takeIf(String::isNotBlank) ?: return null
        val verifier = stored.pendingCodeVerifier?.takeIf(String::isNotBlank) ?: return null
        if (isMalAuthorizationExpired(stored.pendingStartedAtEpochMs, nowMs())) {
            consumePending()
            publish(error = MalAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return authorizationUrl(state, verifier)
    }

    override fun onCancelAuthorization() {
        ensureLoaded()
        consumePending()
        publish(error = null)
    }

    override fun onDisconnectRequested() {
        ensureLoaded()
        val profileId = activeProfileId()
        synchronized(tokenLock) { tokens = null }
        MalAuthStorage.saveTokens(profileId, null)
        MalListCache.clear(profileId)
        MalListRepository.clearLocalState()
        stored = MalStoredAuthState()
        persistMetadata()
        publish(error = null)
    }

    override fun handleAuthCallback(url: String): Boolean {
        if (!isCallback(url)) return false
        ensureLoaded()
        val parsed = runCatching { Url(url) }.getOrNull()
        if (parsed == null) {
            publish(error = MalAuthError.INVALID_CALLBACK)
            return true
        }
        val validation = validateMalCallback(
            code = parsed.parameters["code"],
            callbackState = parsed.parameters["state"],
            failure = parsed.parameters["error"],
            stored = stored,
            nowEpochMs = nowMs(),
        )
        when (validation) {
            is MalCallbackValidation.Rejected -> {
                if (validation.consumePending) consumePending()
                publish(error = validation.error)
            }
            is MalCallbackValidation.Accepted -> {
                // Consume before network I/O: duplicate callbacks cannot exchange the same grant twice.
                consumePending()
                publish(isLoading = true, error = null)
                val profileId = activeProfileId()
                scope.launch { exchangeAndLoadViewer(profileId, validation.code, validation.verifier) }
            }
        }
        return true
    }

    /** Returns a fresh bearer token, refreshing without a client secret when needed. */
    suspend fun accessTokenOrNull(): String? {
        ensureLoaded()
        val current = synchronized(tokenLock) { tokens } ?: return null
        val expiry = stored.tokenExpiresAtEpochMs ?: return current.accessToken
        if (nowMs() + MAL_TOKEN_REFRESH_SKEW_MS < expiry) return current.accessToken
        return refreshMutex.withLock {
            // Another caller may have refreshed while this coroutine waited for the lock.
            val latest = synchronized(tokenLock) { tokens } ?: return@withLock null
            val latestExpiry = stored.tokenExpiresAtEpochMs
            if (latestExpiry == null || nowMs() + MAL_TOKEN_REFRESH_SKEW_MS < latestExpiry) {
                latest.accessToken
            } else {
                refreshTokens(latest)
            }
        }
    }

    suspend fun refreshViewer() {
        ensureLoaded()
        val profileId = activeProfileId()
        val token = accessTokenOrNull() ?: return
        try {
            val viewer = MalClient.getCurrentUser(token)
            if (loadedProfileId != profileId || ProfileRepository.activeProfileId != profileId) return
            stored = stored.copy(accountId = viewer.id, username = viewer.name, avatarUrl = viewer.picture)
            persistMetadata()
            publish(error = null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "MAL viewer lookup failed" }
            publish(error = MalAuthError.VIEWER_LOOKUP_FAILED)
        }
    }

    private suspend fun exchangeAndLoadViewer(profileId: Int, code: String, verifier: String) {
        try {
            val response = MalClient.exchangeAuthorizationCode(
                clientId = MalConfig.CLIENT_ID,
                redirectUri = MalConfig.REDIRECT_URI,
                code = code,
                codeVerifier = verifier,
            )
            if (profileId != ProfileRepository.activeProfileId) return
            storeTokenResponse(response, previousRefreshToken = null)
            publish(isLoading = true, error = null)
            refreshViewer()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "MAL token exchange failed" }
            if (profileId == ProfileRepository.activeProfileId) {
                publish(error = MalAuthError.TOKEN_EXCHANGE_FAILED)
            }
        }
    }

    private suspend fun refreshTokens(current: MalStoredTokens): String? = try {
        val response = MalClient.refreshToken(MalConfig.CLIENT_ID, current.refreshToken)
        if (!sameProfileStillActive()) null
        else {
            storeTokenResponse(response, current.refreshToken)
            response.accessToken
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        log.w(error) { "MAL token refresh failed" }
        if (error is MalApiException.Unauthorized) clearTokens()
        publish(error = MalAuthError.TOKEN_REFRESH_FAILED)
        null
    }

    private fun storeTokenResponse(response: MalTokenResponse, previousRefreshToken: String?) {
        val refreshToken = response.refreshToken?.takeIf(String::isNotBlank) ?: previousRefreshToken
            ?: throw IllegalStateException("MAL omitted the refresh token")
        val next = MalStoredTokens(
            accessToken = response.accessToken,
            refreshToken = refreshToken,
            tokenType = response.tokenType,
        )
        synchronized(tokenLock) { tokens = next }
        MalAuthStorage.saveTokens(activeProfileId(), json.encodeToString(next))
        stored = stored.copy(
            tokenExpiresAtEpochMs = nowMs() + response.expiresInSeconds.coerceAtLeast(1L) * 1_000L,
        )
        persistMetadata()
    }

    private fun loadFromDisk(profileId: Int) {
        loadedProfileId = profileId
        stored = MalAuthStorage.loadMetadata(profileId)
            ?.let { runCatching { json.decodeFromString<MalStoredAuthState>(it) }.getOrNull() }
            ?: MalStoredAuthState()
        val loadedTokens = MalAuthStorage.loadTokens(profileId)
            ?.let { runCatching { json.decodeFromString<MalStoredTokens>(it) }.getOrNull() }
        synchronized(tokenLock) { tokens = loadedTokens }
        hasLoaded = true
        publish()
    }

    private fun authorizationUrl(state: String, verifier: String): String = buildString {
        append(AUTHORIZE_URL)
        append("?response_type=code")
        append("&client_id=").append(MalConfig.CLIENT_ID.encodeURLParameter())
        append("&redirect_uri=").append(MalConfig.REDIRECT_URI.encodeURLParameter())
        append("&code_challenge=").append(verifier.encodeURLParameter())
        append("&code_challenge_method=plain")
        append("&state=").append(state.encodeURLParameter())
    }

    private fun consumePending() {
        stored = stored.withoutPendingAuthorization()
        persistMetadata()
    }

    private fun clearTokens() {
        synchronized(tokenLock) { tokens = null }
        MalAuthStorage.saveTokens(activeProfileId(), null)
        stored = stored.copy(tokenExpiresAtEpochMs = null)
        persistMetadata()
    }

    private fun persistMetadata() = MalAuthStorage.saveMetadata(activeProfileId(), json.encodeToString(stored))

    private fun publish(isLoading: Boolean = false, error: MalAuthError? = _uiState.value.error) {
        val connected = synchronized(tokenLock) { tokens != null }
        _isAuthenticated.value = connected
        _uiState.value = MalAuthUiState(
            mode = when {
                connected -> MalConnectionMode.CONNECTED
                stored.pendingState != null -> MalConnectionMode.AWAITING_APPROVAL
                else -> MalConnectionMode.DISCONNECTED
            },
            credentialsConfigured = MalConfig.isConfigured,
            isLoading = isLoading,
            username = stored.username,
            accountId = stored.accountId,
            avatarUrl = stored.avatarUrl,
            tokenExpiresAtEpochMs = stored.tokenExpiresAtEpochMs,
            pendingAuthorizationStartedAtEpochMs = stored.pendingStartedAtEpochMs,
            error = error,
        )
    }

    private fun isCallback(url: String): Boolean {
        val parsed = runCatching { Url(url.trim()) }.getOrNull() ?: return false
        return parsed.protocol.name.equals("nuvio", ignoreCase = true) &&
            parsed.host.equals(CALLBACK_HOST, ignoreCase = true) &&
            parsed.segments.firstOrNull { it.isNotBlank() }?.equals(CALLBACK_PATH, ignoreCase = true) == true
    }

    private fun activeProfileId(): Int = loadedProfileId ?: ProfileRepository.activeProfileId
    private fun sameProfileStillActive(): Boolean = loadedProfileId == ProfileRepository.activeProfileId
    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()
}
