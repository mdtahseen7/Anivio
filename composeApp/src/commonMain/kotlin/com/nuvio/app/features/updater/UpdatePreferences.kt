package com.nuvio.app.features.updater

import com.nuvio.app.core.build.AppVersionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UpdateChannel { STABLE, BETA }

internal fun isReleaseInUpdateChannel(channel: UpdateChannel, draft: Boolean, prerelease: Boolean, tag: String): Boolean {
    if (draft) return false
    val previewTag = Regex("(?i)(alpha|beta|preview|(?:^|[.\\-_])rc(?:[.\\-_0-9]|$))").containsMatchIn(tag)
    return channel == UpdateChannel.BETA || (!prerelease && !previewTag)
}

internal object UpdatePreferences {
    val shared get() = this
    private val state = MutableStateFlow(
        AppUpdaterPlatform.getUpdateChannel()?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull() }
            ?: if (AppVersionConfig.VERSION_NAME.contains("beta", true) || AppVersionConfig.VERSION_NAME.contains("alpha", true)) UpdateChannel.BETA else UpdateChannel.STABLE
    )
    val channel = state.asStateFlow()
    fun setChannel(value: UpdateChannel) {
        AppUpdaterPlatform.setUpdateChannel(value.name)
        AppUpdaterPlatform.setIgnoredTag(null)
        state.value = value
    }
}
