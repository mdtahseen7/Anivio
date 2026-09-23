package com.nuvio.app.core.sync

internal expect object ProviderCredentialCrypto {
    fun encrypt(plain: String, userId: String): String
    fun decrypt(cipher: String, userId: String): String
}
