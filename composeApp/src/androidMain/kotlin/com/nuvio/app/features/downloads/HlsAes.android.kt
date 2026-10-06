package com.nuvio.app.features.downloads

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal actual fun hlsAes128CbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
    val keySpec = SecretKeySpec(key, "AES")
    val ivSpec = IvParameterSpec(iv)
    // Try standard PKCS7 (what RFC 8216 mandates); fall back to NoPadding for the stray provider
    // that ships block-aligned, unpadded segments (PKCS7 would throw on those).
    return runCatching {
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            doFinal(data)
        }
    }.getOrElse {
        Cipher.getInstance("AES/CBC/NoPadding").run {
            init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            doFinal(data)
        }
    }
}
