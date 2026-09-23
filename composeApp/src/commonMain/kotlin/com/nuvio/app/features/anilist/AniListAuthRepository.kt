package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.core.anilist.AniListClient
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.tracking.TrackingAuthProvider
import com.nuvio.app.features.tracking.TrackingCapability
import com.nuvio.app.features.tracking.TrackingProviderDescriptor
import com.nuvio.app.features.tracking.TrackingProviderId
import com.nuvio.app.features.tracking.TrackingProviderRegistry
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * AniList account connection, using the **implicit** OAuth grant.
 *
 * AniList does not support PKCE, and the authorization-code grant would require shipping a client
 * secret inside the app. Implicit grant avoids both: the access token comes straight back in the
 * redirect fragment, needs no secret, and AniList issues it with a one-year lifetime, so there is
 * no refresh flow to maintain either.
 *
 * The redirect URL registered on the AniList developer app must match [AniListConfig.REDIRECT_URI]
 * exactly, and `nuvio://auth/...` links already route here through `handleAppUrl`.
 */
object AniListAuthRepository : TrackingAuthProvider {
    private const val AUTHORIZE_URL = "https://anilist.co/api/v2/oauth/authorize"
    private const val CALLBACK_HOST = "auth"
    private const val CALLBACK_PATH_SEGMENT = "anilist"
    private const val STATE_BYTES = 16

    private val log = Logger.withTag("AniListAuth")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(AniListAuthUiState())
    val uiState: StateFlow<AniListAuthUiState> = _uiState.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    override val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    override val descriptor = TrackingProviderDescriptor(
        id = TrackingProviderId.ANILIST,
        displayName = "AniList",
        // COMMENTS is absent because AniList activity replies are not surfaced in the app, and
        // RECOMMENDATIONS because "More like this" is served from TMDB.
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
    private var storedState = AniListStoredAuthState()
    private var accessToken: String? = null

    init {
        TrackingProviderRegistry.register(this)
    }

    /** The bearer token for authenticated AniList queries, or null when disconnected. */
    fun accessTokenOrNull(): String? {
        ensureLoaded()
        return accessToken?.takeIf { it.isNotBlank() }
    }

    fun snapshot(): AniListAuthUiState {
        ensureLoaded()
        return uiState.value
    }

    fun hasRequiredCredentials(): Boolean = AniListConfig.isConfigured

    override fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    override fun onProfileChanged() {
        loadFromDisk()
        scope.launch { AniListClient.clearCache() }
    }

    override fun clearLocalState() {
        hasLoaded = false
        storedState = AniListStoredAuthState()
        accessToken = null
        publish()
    }

    override fun removeStoredProfile(profileId: Int) {
        AniListAuthStorage.removeProfile(profileId)
    }

    private fun loadFromDisk() {
        storedState = AniListAuthStorage.loadMetadataPayload()
            ?.let { payload -> runCatching { json.decodeFromString<AniListStoredAuthState>(payload) }.getOrNull() }
            ?: AniListStoredAuthState()
        accessToken = AniListAuthStorage.loadAccessToken()?.takeIf { it.isNotBlank() }
        hasLoaded = true
        publish()
    }

    private fun persistMetadata() {
        AniListAuthStorage.saveMetadataPayload(json.encodeToString(storedState))
    }

    private fun publish(
        isLoading: Boolean = false,
        error: AniListAuthError? = _uiState.value.error,
    ) {
        val connected = accessToken?.isNotBlank() == true
        val mode = when {
            connected -> AniListConnectionMode.CONNECTED
            storedState.pendingAuthorizationState != null -> AniListConnectionMode.AWAITING_APPROVAL
            else -> AniListConnectionMode.DISCONNECTED
        }
        _isAuthenticated.value = connected
        _uiState.value = AniListAuthUiState(
            mode = mode,
            credentialsConfigured = hasRequiredCredentials(),
            isLoading = isLoading,
            username = storedState.username,
            accountId = storedState.accountId,
            avatarUrl = storedState.avatarUrl,
            bannerUrl = storedState.bannerUrl,
            tokenExpiresAtEpochMs = storedState.tokenExpiresAtEpochMs,
            pendingAuthorizationStartedAtEpochMs = storedState.pendingAuthorizationStartedAtEpochMs,
            error = error,
        )
    }

    /** Starts an authorization and returns the URL the caller should open in a browser. */
    fun onConnectRequested(): String? {
        ensureLoaded()
        if (!hasRequiredCredentials()) {
            publish(error = AniListAuthError.MISSING_CLIENT_ID)
            return null
        }

        val state = AniListAuthRandom.secureRandomBytes(STATE_BYTES).toHexString()
        storedState = storedState.copy(
            pendingAuthorizationState = state,
            pendingAuthorizationStartedAtEpochMs = nowMs(),
        )
        persistMetadata()
        publish(error = null)
        return authorizationUrl(state)
    }

    /** Re-opens an authorization already in flight, or null when it has aged out. */
    fun pendingAuthorizationUrl(): String? {
        ensureLoaded()
        val state = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank) ?: return null
        if (isAniListAuthorizationExpired(storedState.pendingAuthorizationStartedAtEpochMs, nowMs())) {
            storedState = storedState.withoutPendingAuthorization()
            persistMetadata()
            publish(error = AniListAuthError.AUTHORIZATION_EXPIRED)
            return null
        }
        return authorizationUrl(state)
    }

    fun onCancelAuthorization() {
        ensureLoaded()
        storedState = storedState.withoutPendingAuthorization()
        persistMetadata()
        publish(error = null)
    }

    fun onDisconnectRequested() {
        ensureLoaded()
        accessToken = null
        AniListAuthStorage.saveAccessToken(null)
        storedState = AniListStoredAuthState()
        persistMetadata()
        publish(error = null)
        AniListStatisticsRepository.clearLocalState()
        scope.launch {
            AniListClient.clearCache()
            // Nobody is signed in any more, so the cached lists must not survive to the next launch.
            AniListListCache.clear()
        }
    }

    private fun authorizationUrl(state: String): String = buildString {
        append(AUTHORIZE_URL)
        append("?client_id=").append(AniListConfig.CLIENT_ID.encodeURLParameter())
        append("&response_type=token")
        // Deliberately no `redirect_uri`: AniList's implicit grant redirects to the single Redirect
        // URL registered on the developer app, and passing a value that disagrees with it turns a
        // working sign-in into a hard error. AniListConfig.REDIRECT_URI documents what has to be
        // registered there; the callback itself is matched by scheme/host/path below.
        append("&state=").append(state.encodeURLParameter())
    }

    /**
     * Implicit grant returns everything in the URL **fragment**, so read that first and only fall
     * back to the query string (which is where a denied consent can surface instead).
     */
    override fun handleAuthCallback(url: String): Boolean {
        if (!isAniListCallback(url)) return false
        ensureLoaded()

        val parsed = runCatching { Url(url) }.getOrNull() ?: return false
        val fragment = parseQueryString(parsed.fragment)
        val token = fragment["access_token"]?.trim()?.takeIf { it.isNotBlank() }
        val callbackState = fragment["state"]?.trim() ?: parsed.parameters["state"]?.trim()
        val failure = fragment["error"]?.trim() ?: parsed.parameters["error"]?.trim()

        val expectedState = storedState.pendingAuthorizationState?.takeIf(String::isNotBlank)

        if (!failure.isNullOrBlank()) {
            log.w { "AniList authorization refused: $failure" }
            storedState = storedState.withoutPendingAuthorization()
            persistMetadata()
            publish(error = AniListAuthError.AUTHORIZATION_REVOKED)
            return true
        }

        if (token == null) {
            publish(error = AniListAuthError.INVALID_CALLBACK)
            return true
        }

        // AniList echoes `state` back, so a mismatch means this callback did not come from the
        // authorization this app started — refuse it rather than adopt an unknown account.
        if (expectedState != null && callbackState != null && callbackState != expectedState) {
            log.w { "AniList callback state mismatch" }
            publish(error = AniListAuthError.INVALID_CALLBACK_STATE)
            return true
        }

        accessToken = token
        AniListAuthStorage.saveAccessToken(token)
        storedState = storedState
            .withoutPendingAuthorization()
            .copy(tokenExpiresAtEpochMs = fragment.expiresAtEpochMs())
        persistMetadata()
        publish(isLoading = true, error = null)

        scope.launch {
            AniListClient.clearCache()
            refreshViewer()
        }
        return true
    }

    /** Loads (or reloads) the signed-in account's identity for the settings card. */
    suspend fun refreshViewer() {
        val token = accessTokenOrNull() ?: return
        try {
            val data = AniListClient.query(
                query = "query { Viewer { id name bannerImage avatar { large medium } } }",
                forceRefresh = true,
                accessToken = token,
            )
            val viewer = data["Viewer"] as? JsonObject
            if (viewer == null) {
                publish(error = AniListAuthError.VIEWER_LOOKUP_FAILED)
                return
            }
            val avatar = viewer["avatar"] as? JsonObject
            storedState = storedState.copy(
                accountId = viewer["id"]?.jsonPrimitive?.intOrNull,
                username = viewer["name"]?.jsonPrimitive?.contentOrNullSafe(),
                avatarUrl = (avatar?.get("large") ?: avatar?.get("medium"))?.jsonPrimitive?.contentOrNullSafe(),
                bannerUrl = viewer["bannerImage"]?.jsonPrimitive?.contentOrNullSafe(),
            )
            persistMetadata()
            publish(error = null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "AniList viewer lookup failed" }
            publish(error = AniListAuthError.VIEWER_LOOKUP_FAILED)
        }
    }

    private fun isAniListCallback(url: String): Boolean {
        val parsed = runCatching { Url(url.trim()) }.getOrNull() ?: return false
        if (!parsed.protocol.name.equals("nuvio", ignoreCase = true)) return false
        if (!parsed.host.equals(CALLBACK_HOST, ignoreCase = true)) return false
        return parsed.segments
            .firstOrNull { it.isNotBlank() }
            ?.equals(CALLBACK_PATH_SEGMENT, ignoreCase = true) == true
    }

    private fun nowMs(): Long = EpisodeReleaseDatePlatform.nowEpochMs()

// ANILIST_AUTH_BODY
}

private val HEX_DIGITS = "0123456789abcdef"

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    this@toHexString.forEach { byte ->
        val value = byte.toInt() and 0xFF
        append(HEX_DIGITS[value shr 4])
        append(HEX_DIGITS[value and 0x0F])
    }
}

private fun Parameters.expiresAtEpochMs(): Long? =
    this["expires_in"]
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?.let { seconds -> EpisodeReleaseDatePlatform.nowEpochMs() + seconds * 1_000L }

private fun JsonPrimitive.contentOrNullSafe(): String? =
    contentOrNull?.trim()?.takeIf { it.isNotBlank() }
