package com.nuvio.app.core.sync

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal actual object ProviderCredentialCrypto {
    @OptIn(ExperimentalEncodingApi::class)
    actual fun encrypt(plain: String, userId: String): String {
        if (plain.isBlank() || userId.isBlank()) return plain
        return try {
            // Simple obfuscation + per-user base64; real AES via CommonCrypto can replace later
            // Using userId as pepper makes ciphertext per-user, plus RLS already isolates.
            val peppered = "$userId|$plain"
            Base64.UrlSafe.encode(peppered.encodeToByteArray())
        } catch (_: Exception) {
            plain
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    actual fun decrypt(cipher: String, userId: String): String {
        if (cipher.isBlank() || userId.isBlank()) return cipher
        return try {
            val decoded = Base64.UrlSafe.decode(cipher).decodeToString()
            val prefix = "$userId|"
            if (decoded.startsWith(prefix)) decoded.removePrefix(prefix) else decoded
        } catch (_: Exception) {
            cipher
        }
    }
}
