package com.nuvio.app.features.mal

import java.security.SecureRandom

internal object MalAuthRandom {
    private const val PKCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private val random = SecureRandom()

    fun state(): String = bytes(24).toHexString()

    /** RFC 7636 permits 43-128 unreserved characters; MAL requires the plain challenge method. */
    fun codeVerifier(length: Int = 96): String {
        require(length in 43..128)
        return buildString(length) {
            repeat(length) { append(PKCE_ALPHABET[random.nextInt(PKCE_ALPHABET.length)]) }
        }
    }

    private fun bytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}
