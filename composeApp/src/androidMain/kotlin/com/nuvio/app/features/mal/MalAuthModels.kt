package com.nuvio.app.features.mal

import kotlinx.serialization.Serializable

internal const val MAL_AUTHORIZATION_TTL_MS = 15 * 60 * 1_000L
internal const val MAL_TOKEN_REFRESH_SKEW_MS = 60 * 1_000L

@Serializable
internal data class MalStoredAuthState(
    val username: String? = null,
    val accountId: Int? = null,
    val avatarUrl: String? = null,
    val tokenExpiresAtEpochMs: Long? = null,
    val pendingState: String? = null,
    val pendingCodeVerifier: String? = null,
    val pendingStartedAtEpochMs: Long? = null,
) {
    fun withoutPendingAuthorization(): MalStoredAuthState = copy(
        pendingState = null,
        pendingCodeVerifier = null,
        pendingStartedAtEpochMs = null,
    )
}

@Serializable
internal data class MalStoredTokens(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
)

internal fun isMalAuthorizationExpired(startedAtEpochMs: Long?, nowEpochMs: Long): Boolean {
    val startedAt = startedAtEpochMs ?: return true
    val age = nowEpochMs - startedAt
    return age < 0L || age > MAL_AUTHORIZATION_TTL_MS
}

internal sealed interface MalCallbackValidation {
    data class Accepted(val code: String, val verifier: String) : MalCallbackValidation
    data class Rejected(val error: MalAuthError, val consumePending: Boolean) : MalCallbackValidation
}

internal fun validateMalCallback(
    code: String?,
    callbackState: String?,
    failure: String?,
    stored: MalStoredAuthState,
    nowEpochMs: Long,
): MalCallbackValidation {
    if (!failure.isNullOrBlank()) {
        return MalCallbackValidation.Rejected(MalAuthError.AUTHORIZATION_REVOKED, consumePending = true)
    }
    val expectedState = stored.pendingState?.takeIf(String::isNotBlank)
        ?: return MalCallbackValidation.Rejected(MalAuthError.INVALID_CALLBACK_STATE, consumePending = false)
    val verifier = stored.pendingCodeVerifier?.takeIf(String::isNotBlank)
        ?: return MalCallbackValidation.Rejected(MalAuthError.INVALID_CALLBACK_STATE, consumePending = true)
    if (isMalAuthorizationExpired(stored.pendingStartedAtEpochMs, nowEpochMs)) {
        return MalCallbackValidation.Rejected(MalAuthError.AUTHORIZATION_EXPIRED, consumePending = true)
    }
    if (callbackState.isNullOrBlank() || callbackState != expectedState) {
        return MalCallbackValidation.Rejected(MalAuthError.INVALID_CALLBACK_STATE, consumePending = false)
    }
    val acceptedCode = code?.trim()?.takeIf(String::isNotBlank)
        ?: return MalCallbackValidation.Rejected(MalAuthError.INVALID_CALLBACK, consumePending = false)
    return MalCallbackValidation.Accepted(code = acceptedCode, verifier = verifier)
}
