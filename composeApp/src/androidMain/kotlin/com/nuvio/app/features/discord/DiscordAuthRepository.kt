package com.nuvio.app.features.discord

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Discord Rich Presence repository: captures the account token, verifies it against the
 * REST API, keeps the gateway connected and translates player updates into presence payloads.
 *
 * Ported from Luna-Native's Kizzy-style implementation (user-gateway presence, external-asset
 * registration for cover art).
 */
internal object DiscordAuthRepository : DiscordAuthController {
    private const val API_BASE = "https://discord.com/api/v9"
    private const val OAUTH_AUTHORIZE_URL = "https://discord.com/oauth2/authorize"
    private const val OAUTH_TOKEN_URL = "https://discord.com/api/oauth2/token"
    private const val OAUTH_REDIRECT_URI = "nuvio://auth/discord"
    private const val OAUTH_SCOPE = "identify"
    private const val OAUTH_STATE_BYTES = 16
    private const val OAUTH_VERIFIER_BYTES = 32

    private val log = Logger.withTag("DiscordRpc")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val externalAssetCache = ConcurrentHashMap<String, String>()

    private val _uiState = MutableStateFlow(DiscordAuthUiState())
    override val uiState: StateFlow<DiscordAuthUiState> = _uiState.asStateFlow()

    private var gateway: DiscordGatewayClient? = null
    private var hasLoaded = false

    private var token: String? = null
        private set

    private var pendingOAuthState: String? = null
    private var pendingCodeVerifier: String? = null
    private var pendingOAuthStartedAtMs: Long = 0L

    override fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        val storedUser = DiscordAuthStorage.loadSession()
        val storedToken = DiscordAuthStorage.loadToken()
        if (storedUser == null || storedToken.isNullOrBlank()) {
            _uiState.value = DiscordAuthUiState(rpcEnabled = DiscordAuthStorage.loadRpcEnabled())
            return
        }
        token = storedToken
        _uiState.value = DiscordAuthUiState(
            mode = DiscordConnectionMode.CONNECTED,
            rpcEnabled = DiscordAuthStorage.loadRpcEnabled(),
            user = storedUser,
        )
        if (_uiState.value.rpcEnabled) {
            connectGateway()
        }
    }

    override suspend fun connectWithToken(rawToken: String): Boolean {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) return false
        _uiState.value = _uiState.value.copy(isVerifying = true, error = null)
        val user = fetchCurrentUser(trimmed)
        if (user == null) {
            _uiState.value = _uiState.value.copy(isVerifying = false, error = DiscordAuthError.TOKEN_INVALID)
            return false
        }
        DiscordAuthStorage.saveSession(trimmed, user)
        token = trimmed
        _uiState.value = DiscordAuthUiState(
            mode = DiscordConnectionMode.CONNECTED,
            isVerifying = false,
            rpcEnabled = _uiState.value.rpcEnabled,
            user = user,
        )
        if (_uiState.value.rpcEnabled) {
            connectGateway()
        }
        return true
    }

    override fun oAuthAuthorizeUrl(): String? {
        val clientId = DiscordConfig.APP_ID.trim()
        if (clientId.isBlank()) {
            _uiState.value = _uiState.value.copy(error = DiscordAuthError.MISSING_CLIENT_ID)
            return null
        }
        val state = generateRandomBase64Url(OAUTH_STATE_BYTES)
        val verifier = generateRandomBase64Url(OAUTH_VERIFIER_BYTES)
        val challenge = pkceChallenge(verifier) ?: return null
        pendingOAuthState = state
        pendingCodeVerifier = verifier
        pendingOAuthStartedAtMs = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(isVerifying = false, error = null)
        return buildString {
            append(OAUTH_AUTHORIZE_URL)
            append("?client_id=").append(urlEncode(clientId))
            append("&response_type=code")
            append("&redirect_uri=").append(urlEncode(OAUTH_REDIRECT_URI))
            append("&scope=").append(urlEncode(OAUTH_SCOPE))
            append("&state=").append(urlEncode(state))
            append("&code_challenge=").append(urlEncode(challenge))
            append("&code_challenge_method=S256")
        }
    }

    override fun handleAuthCallback(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith(OAUTH_REDIRECT_URI, ignoreCase = true)) return false
        val code = extractQueryParam(trimmed, "code")
        val state = extractQueryParam(trimmed, "state")
        val error = extractQueryParam(trimmed, "error")
        if (!error.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = DiscordAuthError.OAUTH_FAILED)
            clearPendingOAuth()
            return true
        }
        if (code.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = DiscordAuthError.OAUTH_FAILED)
            return true
        }
        val expectedState = pendingOAuthState
        if (expectedState != null && state != expectedState) {
            _uiState.value = _uiState.value.copy(error = DiscordAuthError.OAUTH_FAILED)
            clearPendingOAuth()
            return true
        }
        if (System.currentTimeMillis() - pendingOAuthStartedAtMs > 10 * 60 * 1000L) {
            _uiState.value = _uiState.value.copy(error = DiscordAuthError.OAUTH_FAILED)
            clearPendingOAuth()
            return true
        }
        val verifier = pendingCodeVerifier
        if (verifier.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = DiscordAuthError.OAUTH_FAILED)
            return true
        }
        // Exchange code for token
        _uiState.value = _uiState.value.copy(isVerifying = true, error = null)
        scope.launch {
            val tokenResult = exchangeCodeForToken(code, verifier)
            if (tokenResult == null) {
                _uiState.value = _uiState.value.copy(isVerifying = false, error = DiscordAuthError.OAUTH_FAILED)
                clearPendingOAuth()
                return@launch
            }
            clearPendingOAuth()
            // Discord OAuth token is Bearer; store with prefix so fetchCurrentUser sends correct header
            val bearerToken = "Bearer ${tokenResult.trim()}"
            val user = fetchCurrentUser(bearerToken)
            if (user == null) {
                _uiState.value = _uiState.value.copy(isVerifying = false, error = DiscordAuthError.TOKEN_INVALID)
                return@launch
            }
            DiscordAuthStorage.saveSession(bearerToken, user)
            token = bearerToken
            _uiState.value = DiscordAuthUiState(
                mode = DiscordConnectionMode.CONNECTED,
                isVerifying = false,
                rpcEnabled = _uiState.value.rpcEnabled,
                user = user,
            )
            if (_uiState.value.rpcEnabled) connectGateway()
        }
        return true
    }

    private fun clearPendingOAuth() {
        pendingOAuthState = null
        pendingCodeVerifier = null
        pendingOAuthStartedAtMs = 0L
    }

    private fun generateRandomBase64Url(bytes: Int): String {
        val random = java.security.SecureRandom()
        val buf = ByteArray(bytes)
        random.nextBytes(buf)
        return android.util.Base64.encodeToString(buf, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }

    private fun pkceChallenge(verifier: String): String? = try {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.US_ASCII))
        android.util.Base64.encodeToString(hash, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    private fun urlEncode(value: String): String = try {
        java.net.URLEncoder.encode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val qIndex = url.indexOf('?')
        val query = if (qIndex >= 0) url.substring(qIndex + 1) else return null
        // Handle fragment vs query: Discord may return in query
        val fragmentIndex = query.indexOf('#')
        val cleanQuery = if (fragmentIndex >= 0) query.substring(0, fragmentIndex) else query
        cleanQuery.split('&').forEach { param ->
            val eq = param.indexOf('=')
            if (eq > 0) {
                val k = try { java.net.URLDecoder.decode(param.substring(0, eq), "UTF-8") } catch (_: Exception) { param.substring(0, eq) }
                if (k == key) {
                    val v = param.substring(eq + 1)
                    return try { java.net.URLDecoder.decode(v, "UTF-8") } catch (_: Exception) { v }
                }
            }
        }
        // also check fragment after #
        val hashIndex = url.indexOf('#')
        if (hashIndex >= 0) {
            val frag = url.substring(hashIndex + 1)
            frag.split('&').forEach { param ->
                val eq = param.indexOf('=')
                if (eq > 0) {
                    val k = try { java.net.URLDecoder.decode(param.substring(0, eq), "UTF-8") } catch (_: Exception) { param.substring(0, eq) }
                    if (k == key) {
                        val v = param.substring(eq + 1)
                        return try { java.net.URLDecoder.decode(v, "UTF-8") } catch (_: Exception) { v }
                    }
                }
            }
        }
        return null
    }

    private suspend fun exchangeCodeForToken(code: String, verifier: String): String? {
        val clientId = DiscordConfig.APP_ID.trim()
        val body = buildString {
            append("client_id=").append(urlEncode(clientId))
            append("&grant_type=authorization_code")
            append("&code=").append(urlEncode(code))
            append("&redirect_uri=").append(urlEncode(OAUTH_REDIRECT_URI))
            append("&code_verifier=").append(urlEncode(verifier))
        }
        val response = runCatching {
            httpRequestRaw(
                method = "POST",
                url = OAUTH_TOKEN_URL,
                headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                body = body,
            )
        }.getOrNull() ?: return null
        if (response.status != 200) return null
        return runCatching {
            val obj = json.parseToJsonElement(response.body).jsonObject
            obj["access_token"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    override fun disconnect() {
        DiscordAuthStorage.clearSession()
        token = null
        gateway?.disconnect()
        gateway = null
        externalAssetCache.clear()
        _uiState.value = DiscordAuthUiState(rpcEnabled = _uiState.value.rpcEnabled)
    }

    override fun setRpcEnabled(enabled: Boolean) {
        DiscordAuthStorage.saveRpcEnabled(enabled)
        _uiState.value = _uiState.value.copy(rpcEnabled = enabled)
        if (enabled && token != null) {
            connectGateway()
        } else if (!enabled) {
            gateway?.clearPresence()
        }
    }

    override fun updateWatching(activity: DiscordWatchingActivity) {
        if (token == null || !_uiState.value.rpcEnabled) return
        scope.launch(Dispatchers.IO) {
            val largeImage = activity.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                registerExternalAsset(url)
            }
            val presence = buildJsonObject {
                put("since", 0)
                put(
                    "activities",
                    JsonArray(
                        listOf(
                            buildDiscordWatchingActivity(
                                activity = activity,
                                applicationId = DiscordConfig.APP_ID,
                                largeImageKey = largeImage,
                                startEpochMs = activity.positionMs.takeIf { it > 0L }
                                    ?.let { System.currentTimeMillis() - it },
                            ),
                        ),
                    ),
                )
                put("status", "online")
                put("afk", false)
            }
            gateway?.setPresence(presence)
        }
    }

    override fun clearWatching() {
        if (token == null) return
        gateway?.clearPresence()
    }

    private fun connectGateway() {
        val accountToken = token ?: return
        val client = synchronized(this) {
            gateway ?: DiscordGatewayClient(scope) { status ->
                _uiState.value = _uiState.value.copy(gatewayStatus = status)
            }.also { gateway = it }
        }
        client.connect(accountToken)
    }

    private suspend fun fetchCurrentUser(accountToken: String): DiscordUser? {
        val response = runCatching {
            httpRequestRaw(
                method = "GET",
                url = "$API_BASE/users/@me",
                headers = mapOf("Authorization" to accountToken),
                body = "",
            )
        }.getOrNull() ?: return null
        if (response.status != 200) return null
        return runCatching {
            payloadAsUser(json.parseToJsonElement(response.body).jsonObject)
        }.getOrNull()
    }

    private fun payloadAsUser(payload: kotlinx.serialization.json.JsonObject): DiscordUser {
        val id = payload["id"]?.jsonPrimitive?.content.orEmpty()
        val username = payload["username"]?.jsonPrimitive?.content.orEmpty()
        val globalName = payload["global_name"]?.jsonPrimitive?.content
        val avatarHash = payload["avatar"]?.jsonPrimitive?.content
        val avatarUrl = avatarHash?.takeIf { it.isNotBlank() }?.let { hash ->
            "https://cdn.discordapp.com/avatars/$id/$hash.png?size=128"
        }
        return DiscordUser(
            id = id,
            username = username,
            globalName = globalName,
            avatarUrl = avatarUrl,
        )
    }

    /** Register a remote image so it can be shown as the activity's large asset. */
    private suspend fun registerExternalAsset(url: String): String? {
        val appId = DiscordConfig.APP_ID
        val accountToken = token
        if (appId.isBlank() || accountToken == null) return null
        externalAssetCache[url]?.let { return it }
        val response = runCatching {
            httpRequestRaw(
                method = "POST",
                url = "$API_BASE/applications/$appId/external-assets",
                headers = mapOf(
                    "Authorization" to accountToken,
                    "Content-Type" to "application/json",
                ),
                body = buildJsonObject {
                    put("urls", JsonArray(listOf(JsonPrimitive(url))))
                }.toString(),
            )
        }.getOrNull() ?: return null
        if (response.status != 200) return null
        val assetPath = runCatching {
            json.parseToJsonElement(response.body)
                .jsonArray.firstOrNull()?.jsonObject?.get("external_asset_path")
                ?.jsonPrimitive?.content
        }.getOrNull() ?: return null
        val key = "mp:$assetPath"
        externalAssetCache[url] = key
        log.i { "registered external asset" }
        return key
    }
}
