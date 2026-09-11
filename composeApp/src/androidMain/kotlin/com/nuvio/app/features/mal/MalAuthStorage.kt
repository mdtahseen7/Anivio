package com.nuvio.app.features.mal

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.app.core.storage.ProfileScopedKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Profile-scoped metadata in prefs; access and refresh tokens are AES-GCM sealed by Android Keystore. */
object MalAuthStorage {
    private const val PREFERENCES_NAME = "nuvio_mal_auth"
    private const val METADATA_KEY = "mal_auth_metadata"
    private const val TOKENS_KEY = "mal_auth_tokens"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "nuvio.mal.credentials.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    internal fun loadMetadata(profileId: Int): String? =
        preferences?.getString(ProfileScopedKey.of(METADATA_KEY, profileId), null)

    internal fun saveMetadata(profileId: Int, payload: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(METADATA_KEY, profileId), payload)?.apply()
    }

    internal fun loadTokens(profileId: Int): String? {
        val key = ProfileScopedKey.of(TOKENS_KEY, profileId)
        val stored = preferences?.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }
            .onFailure { preferences?.edit()?.remove(key)?.apply() }
            .getOrNull()
    }

    internal fun saveTokens(profileId: Int, payload: String?) {
        val key = ProfileScopedKey.of(TOKENS_KEY, profileId)
        val editor = preferences?.edit() ?: return
        if (payload.isNullOrBlank()) editor.remove(key).apply()
        else editor.putString(key, encrypt(payload)).apply()
    }

    internal fun removeProfile(profileId: Int) {
        preferences?.edit()
            ?.remove(ProfileScopedKey.of(METADATA_KEY, profileId))
            ?.remove(ProfileScopedKey.of(TOKENS_KEY, profileId))
            ?.apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        return "${cipher.iv.toBase64()}.${cipher.doFinal(value.encodeToByteArray()).toBase64()}"
    }

    private fun decrypt(value: String): String {
        val separator = value.indexOf('.')
        require(separator > 0 && separator < value.lastIndex) { "Invalid encrypted credential" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, value.substring(0, separator).fromBase64()),
        )
        return cipher.doFinal(value.substring(separator + 1).fromBase64()).decodeToString()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
}
