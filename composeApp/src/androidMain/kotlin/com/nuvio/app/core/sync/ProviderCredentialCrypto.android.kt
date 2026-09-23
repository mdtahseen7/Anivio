package com.nuvio.app.core.sync

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual object ProviderCredentialCrypto {
    private const val PEPPER = "anivio_provider_credential_v1"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN = 128

    private fun deriveKey(userId: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest((userId + PEPPER).toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    actual fun encrypt(plain: String, userId: String): String {
        if (plain.isBlank() || userId.isBlank()) return plain
        return try {
            val key = deriveKey(userId)
            val iv = ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv, 0, GCM_IV_LEN))
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = iv + encrypted
            Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
        } catch (_: Exception) {
            plain
        }
    }

    actual fun decrypt(cipher: String, userId: String): String {
        if (cipher.isBlank() || userId.isBlank()) return cipher
        return try {
            val decoded = Base64.getUrlDecoder().decode(cipher)
            if (decoded.size <= GCM_IV_LEN) return cipher
            val iv = decoded.copyOfRange(0, GCM_IV_LEN)
            val encrypted = decoded.copyOfRange(GCM_IV_LEN, decoded.size)
            val key = deriveKey(userId)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv, 0, GCM_IV_LEN))
            String(c.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            // Fallback: maybe stored plaintext from before encryption
            cipher
        }
    }
}
