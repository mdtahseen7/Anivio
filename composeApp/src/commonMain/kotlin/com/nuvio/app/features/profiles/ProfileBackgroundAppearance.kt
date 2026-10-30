package com.nuvio.app.features.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal const val DEFAULT_PROFILE_BACKGROUND_BRIGHTNESS = 0.5f
internal const val MIN_PROFILE_BACKGROUND_BRIGHTNESS = 0.2f
internal const val MAX_PROFILE_BACKGROUND_BRIGHTNESS = 1f

internal fun normalizeProfileBackgroundBrightness(value: Float): Float =
    if (value.isFinite()) {
        value.coerceIn(MIN_PROFILE_BACKGROUND_BRIGHTNESS, MAX_PROFILE_BACKGROUND_BRIGHTNESS)
    } else {
        DEFAULT_PROFILE_BACKGROUND_BRIGHTNESS
    }

internal fun profileBackgroundOverlayAlpha(brightness: Float): Float =
    1f - normalizeProfileBackgroundBrightness(brightness)

internal expect object ProfileBackgroundAppearanceStorage {
    fun loadBrightness(profileId: Int): Float?
    fun saveBrightness(profileId: Int, brightness: Float)
}

object ProfileBackgroundAppearanceRepository {
    private val _brightness = MutableStateFlow(DEFAULT_PROFILE_BACKGROUND_BRIGHTNESS)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private var loadedProfileId: Int? = null

    fun ensureLoaded(profileId: Int = ProfileRepository.activeProfileId) {
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        _brightness.value = brightnessFor(profileId)
    }

    fun brightnessFor(profileId: Int): Float =
        normalizeProfileBackgroundBrightness(
            ProfileBackgroundAppearanceStorage.loadBrightness(profileId)
                ?: DEFAULT_PROFILE_BACKGROUND_BRIGHTNESS,
        )

    fun update(profileId: Int, brightness: Float) {
        val normalized = normalizeProfileBackgroundBrightness(brightness)
        ProfileBackgroundAppearanceStorage.saveBrightness(profileId, normalized)
        if (loadedProfileId == profileId) _brightness.value = normalized
    }

    fun onProfileChanged(profileId: Int) {
        loadedProfileId = null
        ensureLoaded(profileId)
    }
}
