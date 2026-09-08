package com.nuvio.app.features.anilist

import kotlinx.serialization.Serializable

enum class AniListConnectionMode {
    DISCONNECTED,
    AWAITING_APPROVAL,
    CONNECTED,
}

enum class AniListAuthError {
    MISSING_CLIENT_ID,
    INVALID_CALLBACK,
    INVALID_CALLBACK_STATE,
    AUTHORIZATION_EXPIRED,
    VIEWER_LOOKUP_FAILED,
    AUTHORIZATION_REVOKED,
}

data class AniListAuthUiState(
    val mode: AniListConnectionMode = AniListConnectionMode.DISCONNECTED,
    val credentialsConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val username: String? = null,
    val accountId: Int? = null,
    val avatarUrl: String? = null,
    /** The banner the user set on their AniList profile, for the library header backdrop. */
    val bannerUrl: String? = null,
    val tokenExpiresAtEpochMs: Long? = null,
    val pendingAuthorizationStartedAtEpochMs: Long? = null,
    val error: AniListAuthError? = null,
)

/**
 * Persisted alongside the access token (which is kept separately, encrypted). Nothing here is
 * secret — it is the identity the settings card shows plus the in-flight authorization bookkeeping.
 */
@Serializable
internal data class AniListStoredAuthState(
    val username: String? = null,
    val accountId: Int? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val tokenExpiresAtEpochMs: Long? = null,
    val pendingAuthorizationState: String? = null,
    val pendingAuthorizationStartedAtEpochMs: Long? = null,
) {
    fun withoutPendingAuthorization(): AniListStoredAuthState = copy(
        pendingAuthorizationState = null,
        pendingAuthorizationStartedAtEpochMs = null,
    )
}

/** How long a started-but-unfinished browser authorization stays resumable. */
internal const val ANILIST_AUTHORIZATION_TTL_MS = 15 * 60 * 1000L

internal fun isAniListAuthorizationExpired(
    startedAtEpochMs: Long?,
    nowEpochMs: Long,
): Boolean {
    val startedAt = startedAtEpochMs ?: return true
    return nowEpochMs - startedAt > ANILIST_AUTHORIZATION_TTL_MS
}
