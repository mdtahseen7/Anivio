package com.nuvio.app.features.mal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MalConnectionMode {
    DISCONNECTED,
    AWAITING_APPROVAL,
    CONNECTED,
}

enum class MalAuthError {
    MISSING_CLIENT_ID,
    INVALID_CALLBACK,
    INVALID_CALLBACK_STATE,
    AUTHORIZATION_EXPIRED,
    AUTHORIZATION_REVOKED,
    TOKEN_EXCHANGE_FAILED,
    TOKEN_REFRESH_FAILED,
    VIEWER_LOOKUP_FAILED,
}

data class MalAuthUiState(
    val mode: MalConnectionMode = MalConnectionMode.DISCONNECTED,
    val credentialsConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val username: String? = null,
    val accountId: Int? = null,
    val avatarUrl: String? = null,
    val tokenExpiresAtEpochMs: Long? = null,
    val pendingAuthorizationStartedAtEpochMs: Long? = null,
    val error: MalAuthError? = null,
)

/** Common settings-facing surface implemented by the Android MAL repository. */
interface MalAuthController {
    val uiState: StateFlow<MalAuthUiState>
    fun ensureLoaded()
    fun onConnectRequested(): String?
    fun pendingAuthorizationUrl(): String?
    fun onCancelAuthorization()
    fun onDisconnectRequested()
}

/** Installed by Android startup; unavailable elsewhere without adding platform-specific code. */
object MalAuthSettings {
    private val unavailable = object : MalAuthController {
        override val uiState: StateFlow<MalAuthUiState> = MutableStateFlow(MalAuthUiState())
        override fun ensureLoaded() = Unit
        override fun onConnectRequested(): String? = null
        override fun pendingAuthorizationUrl(): String? = null
        override fun onCancelAuthorization() = Unit
        override fun onDisconnectRequested() = Unit
    }

    private var controller: MalAuthController = unavailable

    val uiState: StateFlow<MalAuthUiState>
        get() = controller.uiState

    fun install(controller: MalAuthController) {
        this.controller = controller
        controller.ensureLoaded()
    }

    fun ensureLoaded() = controller.ensureLoaded()
    fun onConnectRequested(): String? = controller.onConnectRequested()
    fun pendingAuthorizationUrl(): String? = controller.pendingAuthorizationUrl()
    fun onCancelAuthorization() = controller.onCancelAuthorization()
    fun onDisconnectRequested() = controller.onDisconnectRequested()
}
