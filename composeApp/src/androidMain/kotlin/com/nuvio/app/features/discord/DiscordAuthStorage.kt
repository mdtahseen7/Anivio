package com.nuvio.app.features.discord

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** SharedPreferences-backed Discord session storage. The token is sensitive — like a password. */
internal object DiscordAuthStorage {
    private const val preferencesName = "nuvio_discord"
    private const val sessionKey = "session"
    private const val rpcEnabledKey = "rpc_enabled"

    @Serializable
    private data class StoredSession(
        val token: String,
        val userId: String,
        val username: String,
        val globalName: String? = null,
        val avatarUrl: String? = null,
    )

    private var preferences: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    fun loadSession(): DiscordUser? {
        val raw = preferences?.getString(sessionKey, null)?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val stored = json.decodeFromString<StoredSession>(raw)
            DiscordUser(
                id = stored.userId,
                username = stored.username,
                globalName = stored.globalName,
                avatarUrl = stored.avatarUrl,
            )
        }.getOrNull()
    }

    fun loadToken(): String? =
        preferences?.getString(sessionKey, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { json.decodeFromString<StoredSession>(raw).token }.getOrNull()
            }

    fun saveSession(token: String, user: DiscordUser) {
        val payload = json.encodeToString(
            StoredSession.serializer(),
            StoredSession(
                token = token,
                userId = user.id,
                username = user.username,
                globalName = user.globalName,
                avatarUrl = user.avatarUrl,
            ),
        )
        preferences?.edit()?.putString(sessionKey, payload)?.apply()
    }

    fun clearSession() {
        preferences?.edit()?.remove(sessionKey)?.apply()
    }

    fun loadRpcEnabled(): Boolean =
        preferences?.getBoolean(rpcEnabledKey, true) ?: true

    fun saveRpcEnabled(enabled: Boolean) {
        preferences?.edit()?.putBoolean(rpcEnabledKey, enabled)?.apply()
    }
}
