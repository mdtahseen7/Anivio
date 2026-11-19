package com.nuvio.app.features.discord

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class DiscordConnectionMode {
    DISCONNECTED,
    CONNECTED,
}

enum class DiscordGatewayStatus {
    OFFLINE,
    CONNECTING,
    ONLINE,
    ERROR,
}

enum class DiscordAuthError {
    TOKEN_INVALID,
    VERIFICATION_FAILED,
    OAUTH_FAILED,
    MISSING_CLIENT_ID,
}

data class DiscordUser(
    val id: String,
    val username: String,
    val globalName: String?,
    val avatarUrl: String?,
)

data class DiscordAuthUiState(
    val mode: DiscordConnectionMode = DiscordConnectionMode.DISCONNECTED,
    val isVerifying: Boolean = false,
    val rpcEnabled: Boolean = true,
    val gatewayStatus: DiscordGatewayStatus = DiscordGatewayStatus.OFFLINE,
    val user: DiscordUser? = null,
    val error: DiscordAuthError? = null,
)

/** One "watching" push from the player; the Android implementation translates it into presence. */
data class DiscordWatchingActivity(
    val contentTitle: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val imageUrl: String? = null,
    val isPlaying: Boolean,
    val positionMs: Long = 0L,
)

/** Common settings/player-facing surface implemented by the Android Discord repository. */
interface DiscordAuthController {
    val uiState: StateFlow<DiscordAuthUiState>
    fun ensureLoaded()
    suspend fun connectWithToken(rawToken: String): Boolean
    fun disconnect()
    fun setRpcEnabled(enabled: Boolean)
    fun updateWatching(activity: DiscordWatchingActivity)
    fun clearWatching()
    fun oAuthAuthorizeUrl(): String?
    fun handleAuthCallback(url: String): Boolean
}

/**
 * Builds the activity object inside a Gateway presence push (op 3) for one watching update.
 *
 * Pure and deterministic so it can be unit-tested; the Android repository embeds it into the
 * full presence envelope and resolves [imageUrl] into an `external-assets` key first. Mirrors
 * Luna-Native's activity shape.
 *
 * @param startEpochMs playback start anchored to [DiscordWatchingActivity.positionMs], or null
 *   when the position is unknown (elapsed time is omitted from the activity).
 */
fun buildDiscordWatchingActivity(
    activity: DiscordWatchingActivity,
    applicationId: String,
    largeImageKey: String?,
    startEpochMs: Long?,
): JsonObject {
    val appName = activity.contentTitle.ifBlank { DEFAULT_ACTIVITY_NAME }
    val details = buildList {
        add(
            if (activity.seasonNumber != null && activity.episodeNumber != null) {
                "Episode S${activity.seasonNumber}E${activity.episodeNumber}"
            } else {
                "Episode ${activity.episodeNumber ?: ""}".trim()
            },
        )
        activity.episodeTitle?.takeIf { it.isNotBlank() }?.let(::add)
    }.filter { it.isNotBlank() }.joinToString(separator = " — ")

    return buildJsonObject {
        put("name", appName)
        put("type", ACTIVITY_TYPE_WATCHING)
        put("details", details)
        put("state", if (activity.isPlaying) PLAYING_STATE else PAUSED_STATE)
        put("application_id", applicationId)
        startEpochMs?.let { start ->
            put("timestamps", buildJsonObject { put("start", start) })
        }
        if (largeImageKey != null) {
            put(
                "assets",
                buildJsonObject {
                    put("large_image", largeImageKey)
                    put("large_text", appName)
                },
            )
        }
    }
}

private const val ACTIVITY_TYPE_WATCHING = 3
private const val DEFAULT_ACTIVITY_NAME = "Anivio"
private const val PLAYING_STATE = "Watching on Anivio"
private const val PAUSED_STATE = "Paused"

/** Installed by Android startup; a no-op elsewhere so common code can call it freely. */
object DiscordAuth {
    private val unavailable = object : DiscordAuthController {
        override val uiState: StateFlow<DiscordAuthUiState> = MutableStateFlow(DiscordAuthUiState())
        override fun ensureLoaded() = Unit
        override suspend fun connectWithToken(rawToken: String): Boolean = false
        override fun disconnect() = Unit
        override fun setRpcEnabled(enabled: Boolean) = Unit
        override fun updateWatching(activity: DiscordWatchingActivity) = Unit
        override fun clearWatching() = Unit
        override fun oAuthAuthorizeUrl(): String? = null
        override fun handleAuthCallback(url: String): Boolean = false
    }

    private var controller: DiscordAuthController = unavailable

    val uiState: StateFlow<DiscordAuthUiState>
        get() = controller.uiState

    fun install(controller: DiscordAuthController) {
        this.controller = controller
        controller.ensureLoaded()
    }

    fun ensureLoaded() = controller.ensureLoaded()
    suspend fun connectWithToken(rawToken: String): Boolean = controller.connectWithToken(rawToken)
    fun disconnect() = controller.disconnect()
    fun setRpcEnabled(enabled: Boolean) = controller.setRpcEnabled(enabled)
    fun updateWatching(activity: DiscordWatchingActivity) = controller.updateWatching(activity)
    fun clearWatching() = controller.clearWatching()
    fun oAuthAuthorizeUrl(): String? = controller.oAuthAuthorizeUrl()
    fun handleAuthCallback(url: String): Boolean = controller.handleAuthCallback(url)
}
