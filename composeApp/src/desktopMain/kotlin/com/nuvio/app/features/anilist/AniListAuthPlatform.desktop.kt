package com.nuvio.app.features.anilist

import com.nuvio.app.core.storage.DesktopStorage
import com.nuvio.app.core.storage.ProfileScopedKey
import java.security.SecureRandom

internal actual object AniListAuthRandom {
    private val secureRandom = SecureRandom()

    actual fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size).also(secureRandom::nextBytes)
}

/**
 * Mirrors the Android/iOS actuals: metadata and token both profile-scoped.
 *
 * The Android actual seals the token with the OS keystore and iOS with the Keychain; the desktop JVM
 * has no equivalent per-app key store, so the token sits beside other desktop secrets (plugin creds,
 * sync creds) in the app-data properties file at the same trust level.
 * ponytail: plaintext at rest on desktop, add OS-keystore (DPAPI/libsecret/Keychain) binding if the
 * threat model needs it.
 */
internal actual object AniListAuthStorage {
    private const val METADATA_KEY = "anilist_auth_metadata"
    private const val ACCESS_TOKEN_KEY = "anilist_access_token"
    private val store = DesktopStorage.store("nuvio_anilist_auth")

    actual fun loadMetadataPayload(): String? = store.getString(ProfileScopedKey.of(METADATA_KEY))

    actual fun saveMetadataPayload(payload: String) {
        store.putString(ProfileScopedKey.of(METADATA_KEY), payload)
    }

    actual fun loadAccessToken(): String? = store.getString(ProfileScopedKey.of(ACCESS_TOKEN_KEY))

    actual fun saveAccessToken(value: String?) {
        store.putString(ProfileScopedKey.of(ACCESS_TOKEN_KEY), value?.takeIf { it.isNotBlank() })
    }

    actual fun removeProfile(profileId: Int) {
        store.remove(ProfileScopedKey.of(METADATA_KEY, profileId))
        store.remove(ProfileScopedKey.of(ACCESS_TOKEN_KEY, profileId))
    }
}
