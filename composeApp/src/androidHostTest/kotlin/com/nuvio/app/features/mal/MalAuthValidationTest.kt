package com.nuvio.app.features.mal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MalAuthValidationTest {
    @Test
    fun `accepts matching unexpired callback exactly once`() {
        val pending = pending()
        val accepted = validateMalCallback("grant", "expected", null, pending, 2_000L)
        assertEquals(MalCallbackValidation.Accepted("grant", "verifier"), accepted)

        val replay = validateMalCallback(
            "grant",
            "expected",
            null,
            pending.withoutPendingAuthorization(),
            2_000L,
        )
        assertEquals(MalAuthError.INVALID_CALLBACK_STATE, assertIs<MalCallbackValidation.Rejected>(replay).error)
    }

    @Test
    fun `rejects missing and mismatched state without consuming valid request`() {
        listOf(null, "wrong").forEach { state ->
            val result = assertIs<MalCallbackValidation.Rejected>(
                validateMalCallback("grant", state, null, pending(), 2_000L),
            )
            assertEquals(MalAuthError.INVALID_CALLBACK_STATE, result.error)
            assertEquals(false, result.consumePending)
        }
    }

    @Test
    fun `expired authorization is rejected and consumed`() {
        val result = assertIs<MalCallbackValidation.Rejected>(
            validateMalCallback("grant", "expected", null, pending(), 1_000L + MAL_AUTHORIZATION_TTL_MS + 1L),
        )
        assertEquals(MalAuthError.AUTHORIZATION_EXPIRED, result.error)
        assertTrue(result.consumePending)
    }

    @Test
    fun `denied authorization is consumed`() {
        val result = assertIs<MalCallbackValidation.Rejected>(
            validateMalCallback(null, "expected", "access_denied", pending(), 2_000L),
        )
        assertEquals(MalAuthError.AUTHORIZATION_REVOKED, result.error)
        assertTrue(result.consumePending)
    }

    private fun pending() = MalStoredAuthState(
        pendingState = "expected",
        pendingCodeVerifier = "verifier",
        pendingStartedAtEpochMs = 1_000L,
    )
}
