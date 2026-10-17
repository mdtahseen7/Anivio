package com.nuvio.app.features.profile

import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ShareAvatarSource {
    ANIVIO,
    ANILIST,
    HIDDEN,
}

@Serializable
data class ProfileShareSettings(
    val showAniListBackground: Boolean = true,
    val backgroundOpacity: Float = 0.78f,
    val avatarSource: ShareAvatarSource = ShareAvatarSource.ANIVIO,
    val showStats: Boolean = true,
    val showRank: Boolean = true,
    val showAniListUsername: Boolean = true,
    val showMalUsername: Boolean = false,
) {
    fun normalized(): ProfileShareSettings = copy(
        backgroundOpacity = backgroundOpacity.coerceIn(0f, 1f),
    )
}

internal expect object ProfileShareSettingsStorage {
    fun loadPayload(): String?
    fun savePayload(payload: String)
}

object ProfileShareSettingsRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _settings = MutableStateFlow(ProfileShareSettings())
    val settings: StateFlow<ProfileShareSettings> = _settings.asStateFlow()

    private var loadedProfileId: Int? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        _settings.value = ProfileShareSettingsStorage.loadPayload()
            ?.let { payload -> runCatching { json.decodeFromString<ProfileShareSettings>(payload) }.getOrNull() }
            ?.normalized()
            ?: ProfileShareSettings()
    }

    fun onProfileChanged() {
        loadedProfileId = null
        ensureLoaded()
    }

    fun update(transform: (ProfileShareSettings) -> ProfileShareSettings) {
        ensureLoaded()
        val next = transform(_settings.value).normalized()
        _settings.value = next
        ProfileShareSettingsStorage.savePayload(json.encodeToString(next))
    }

    fun reset() {
        ensureLoaded()
        _settings.value = ProfileShareSettings()
        ProfileShareSettingsStorage.savePayload(json.encodeToString(_settings.value))
    }
}
