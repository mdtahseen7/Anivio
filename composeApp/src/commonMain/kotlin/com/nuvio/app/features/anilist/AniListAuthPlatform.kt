package com.nuvio.app.features.anilist

internal expect object AniListAuthStorage {
    fun loadMetadataPayload(): String?
    fun saveMetadataPayload(payload: String)

    /** Stored encrypted at rest — an AniList token can rewrite the user's whole list. */
    fun loadAccessToken(): String?
    fun saveAccessToken(value: String?)

    fun removeProfile(profileId: Int)
}

internal expect object AniListAuthRandom {
    fun secureRandomBytes(size: Int): ByteArray
}
