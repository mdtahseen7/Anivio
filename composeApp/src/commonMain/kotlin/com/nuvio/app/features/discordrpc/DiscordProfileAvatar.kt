package com.nuvio.app.features.discordrpc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object DiscordProfileAvatar {
    private val current = MutableStateFlow<String?>(null)
    val url = current.asStateFlow()

    fun update(url: String?) {
        current.value = url
    }
}
